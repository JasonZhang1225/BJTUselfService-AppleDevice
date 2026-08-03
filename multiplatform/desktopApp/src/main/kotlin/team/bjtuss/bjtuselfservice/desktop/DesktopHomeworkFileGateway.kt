package team.bjtuss.bjtuselfservice.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGatewayFailure
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFilePickResult
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileSaveResult
import team.bjtuss.bjtuselfservice.shared.files.CoursewareDirectoryFile
import team.bjtuss.bjtuselfservice.shared.files.CoursewareDirectoryGateway
import team.bjtuss.bjtuselfservice.shared.files.CoursewareDirectoryOpenResult
import team.bjtuss.bjtuselfservice.shared.files.CoursewareDirectoryWriteSession
import team.bjtuss.bjtuselfservice.shared.files.safeExportFileName
import team.bjtuss.bjtuselfservice.shared.files.safeExportPathSegment

class DesktopHomeworkFileGateway(
    private val owner: () -> Frame?,
) : HomeworkFileGateway, CoursewareDirectoryGateway {
    private val requestMutex = Mutex()

    override val isAvailable: Boolean = true
    override val isDirectoryExportAvailable: Boolean = true

    override suspend fun pickFiles(): HomeworkFilePickResult {
        if (!requestMutex.tryLock()) {
            return HomeworkFilePickResult.Failed(HomeworkFileGatewayFailure.UNAVAILABLE)
        }
        try {
            val selected = runCatching {
                showDialog(FileDialog.LOAD, null, allowMultiple = true)
            }.getOrElse {
                return HomeworkFilePickResult.Failed(HomeworkFileGatewayFailure.IO)
            }
            if (selected.isEmpty()) return HomeworkFilePickResult.Cancelled
            return withContext(Dispatchers.IO) {
                val context = currentCoroutineContext()
                try {
                    HomeworkFilePickResult.Selected(
                        selected.map { file ->
                            context.ensureActive()
                            HomeworkFileContent(
                                fileName = file.name,
                                contentType = Files.probeContentType(file.toPath()) ?: "application/octet-stream",
                                bytes = file.readBytes(),
                            )
                        },
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: SecurityException) {
                    HomeworkFilePickResult.Failed(HomeworkFileGatewayFailure.PERMISSION_DENIED)
                } catch (_: Exception) {
                    HomeworkFilePickResult.Failed(HomeworkFileGatewayFailure.IO)
                }
            }
        } finally {
            requestMutex.unlock()
        }
    }

    override suspend fun saveFile(file: HomeworkFileContent): HomeworkFileSaveResult {
        if (!requestMutex.tryLock()) {
            return HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.UNAVAILABLE)
        }
        try {
            val selected = runCatching {
                showDialog(FileDialog.SAVE, safeExportFileName(file.fileName), allowMultiple = false).singleOrNull()
            }.getOrElse {
                return HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.IO)
            } ?: return HomeworkFileSaveResult.Cancelled
            return withContext(Dispatchers.IO) {
                val context = currentCoroutineContext()
                try {
                    writeFileAtomically(selected, file.bytes) { context.ensureActive() }
                    HomeworkFileSaveResult.Saved
                } catch (error: CancellationException) {
                    throw error
                } catch (_: SecurityException) {
                    HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.PERMISSION_DENIED)
                } catch (_: Exception) {
                    HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.IO)
                }
            }
        } finally {
            requestMutex.unlock()
        }
    }

    override suspend fun openDirectory(directoryName: String): CoursewareDirectoryOpenResult {
        if (!requestMutex.tryLock()) {
            return CoursewareDirectoryOpenResult.Failed(HomeworkFileGatewayFailure.UNAVAILABLE)
        }
        var sessionOpened = false
        var createdRoot: File? = null
        try {
            val destination = runCatching(::showDirectoryDialog).getOrElse {
                return CoursewareDirectoryOpenResult.Failed(HomeworkFileGatewayFailure.IO)
            } ?: return CoursewareDirectoryOpenResult.Cancelled
            val result = withContext(Dispatchers.IO) {
                val context = currentCoroutineContext()
                val root = File(destination, safeExportPathSegment(directoryName))
                if (root.exists()) {
                    return@withContext CoursewareDirectoryOpenResult.Failed(HomeworkFileGatewayFailure.IO)
                }
                try {
                    context.ensureActive()
                    if (!root.mkdirs()) throw IllegalStateException("Unable to create export root")
                    createdRoot = root
                    CoursewareDirectoryOpenResult.Opened(
                        DesktopDirectoryWriteSession(root) { requestMutex.unlock() },
                    )
                } catch (error: CancellationException) {
                    createdRoot?.takeIf { it == root }?.deleteRecursively()
                    throw error
                } catch (_: SecurityException) {
                    createdRoot?.takeIf { it == root }?.deleteRecursively()
                    CoursewareDirectoryOpenResult.Failed(HomeworkFileGatewayFailure.PERMISSION_DENIED)
                } catch (_: Exception) {
                    createdRoot?.takeIf { it == root }?.deleteRecursively()
                    CoursewareDirectoryOpenResult.Failed(HomeworkFileGatewayFailure.IO)
                }
            }
            sessionOpened = result is CoursewareDirectoryOpenResult.Opened
            return result
        } catch (error: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { createdRoot?.deleteRecursively() }
            throw error
        } finally {
            if (!sessionOpened) requestMutex.unlock()
        }
    }

    private inner class DesktopDirectoryWriteSession(
        private val root: File,
        private val onClosed: () -> Unit,
    ) : CoursewareDirectoryWriteSession {
        private val lifecycleMutex = Mutex()
        private var closed = false

        override suspend fun write(file: CoursewareDirectoryFile): HomeworkFileSaveResult =
            lifecycleMutex.withLock {
                if (closed) return@withLock HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.UNAVAILABLE)
                withContext(Dispatchers.IO) {
                    val context = currentCoroutineContext()
                    try {
                        context.ensureActive()
                        val folder = file.relativeFolders.fold(root) { parent, segment ->
                            File(parent, safeExportPathSegment(segment))
                        }
                        if (!folder.exists() && !folder.mkdirs()) {
                            throw IllegalStateException("Unable to create export folder")
                        }
                        val destination = File(folder, safeExportFileName(file.content.fileName))
                        if (destination.exists()) {
                            return@withContext HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.IO)
                        }
                        writeFileAtomically(destination, file.content.bytes) { context.ensureActive() }
                        HomeworkFileSaveResult.Saved
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: SecurityException) {
                        HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.PERMISSION_DENIED)
                    } catch (_: Exception) {
                        HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.IO)
                    }
                }
            }

        override suspend fun commit(): HomeworkFileSaveResult = lifecycleMutex.withLock {
            if (closed) return@withLock HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.UNAVAILABLE)
            closed = true
            onClosed()
            HomeworkFileSaveResult.Saved
        }

        override suspend fun abort() {
            withContext(NonCancellable + Dispatchers.IO) {
                lifecycleMutex.withLock {
                    if (!closed) {
                        runCatching { root.deleteRecursively() }
                        closed = true
                        onClosed()
                    }
                }
            }
        }
    }

    private fun writeFileAtomically(
        destination: File,
        bytes: ByteArray,
        ensureActive: () -> Unit,
    ) {
        val parent = destination.absoluteFile.parentFile
            ?: throw IllegalStateException("Missing destination directory")
        val temporary = Files.createTempFile(parent.toPath(), ".bjtu-save-", ".tmp")
        try {
            ensureActive()
            Files.write(temporary, bytes)
            ensureActive()
            try {
                Files.move(
                    temporary,
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun showDirectoryDialog(): File? {
        var selected: File? = null
        val property = "apple.awt.fileDialogForDirectories"
        val previous = System.getProperty(property)
        val show = {
            try {
                System.setProperty(property, "true")
                val dialog = FileDialog(owner(), "选择课件导出位置", FileDialog.LOAD)
                try {
                    dialog.isVisible = true
                    val directory = dialog.directory
                    val fileName = dialog.file
                    selected = when {
                        directory == null -> null
                        fileName == null -> File(directory)
                        else -> File(directory, fileName)
                    }
                } finally {
                    dialog.dispose()
                }
            } finally {
                if (previous == null) System.clearProperty(property) else System.setProperty(property, previous)
            }
        }
        if (SwingUtilities.isEventDispatchThread()) show() else SwingUtilities.invokeAndWait { show() }
        return selected
    }

    private fun showDialog(
        mode: Int,
        suggestedName: String?,
        allowMultiple: Boolean,
    ): List<File> {
        var selected = emptyList<File>()
        val show = {
            val dialog = FileDialog(owner(), if (mode == FileDialog.LOAD) "选择作业文件" else "保存附件", mode)
            try {
                dialog.isMultipleMode = allowMultiple
                if (suggestedName != null) dialog.file = suggestedName
                dialog.isVisible = true
                selected = if (allowMultiple) {
                    dialog.files.toList()
                } else {
                    val directory = dialog.directory
                    val fileName = dialog.file
                    if (directory == null || fileName == null) emptyList() else listOf(File(directory, fileName))
                }
            } finally {
                dialog.dispose()
            }
        }
        if (SwingUtilities.isEventDispatchThread()) show() else SwingUtilities.invokeAndWait { show() }
        return selected
    }
}
