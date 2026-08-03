package team.bjtuss.bjtuselfservice.shared.files

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.darwin.NSObject
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosHomeworkFileGateway(
    private val owner: () -> UIViewController,
) : HomeworkFileGateway, CoursewareDirectoryGateway {
    private var activeDelegate: IosDocumentPickerDelegate? = null
    private var activePicker: UIDocumentPickerViewController? = null

    override val isAvailable: Boolean = true
    override val isDirectoryExportAvailable: Boolean = true

    override suspend fun pickFiles(): HomeworkFilePickResult {
        if (activeDelegate != null) {
            return HomeworkFilePickResult.Failed(HomeworkFileGatewayFailure.UNAVAILABLE)
        }
        return suspendCancellableCoroutine { continuation ->
            lateinit var delegate: IosDocumentPickerDelegate
            delegate = IosDocumentPickerDelegate(
                onPicked = { urls ->
                    val result = readFiles(urls)
                    finish(delegate, continuation, result)
                },
                onCancelled = {
                    finish(delegate, continuation, HomeworkFilePickResult.Cancelled)
                },
            )
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypeData),
                asCopy = true,
            ).apply {
                allowsMultipleSelection = true
                this.delegate = delegate
            }
            activeDelegate = delegate
            activePicker = picker
            continuation.invokeOnCancellation {
                cancel(delegate)
            }
            owner().presentViewController(picker, animated = true, completion = null)
        }
    }

    override suspend fun saveFile(file: HomeworkFileContent): HomeworkFileSaveResult {
        if (activeDelegate != null) {
            return HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.UNAVAILABLE)
        }
        val temporary = file.writeTemporaryExport()
            ?: return HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.IO)
        return suspendCancellableCoroutine { continuation ->
            lateinit var delegate: IosDocumentPickerDelegate
            delegate = IosDocumentPickerDelegate(
                onPicked = {
                    removeTemporary(temporary.directoryUrl)
                    finish(delegate, continuation, HomeworkFileSaveResult.Saved)
                },
                onCancelled = {
                    removeTemporary(temporary.directoryUrl)
                    finish(delegate, continuation, HomeworkFileSaveResult.Cancelled)
                },
            )
            val picker = UIDocumentPickerViewController(
                forExportingURLs = listOf(temporary.fileUrl),
                asCopy = true,
            ).apply {
                this.delegate = delegate
            }
            activeDelegate = delegate
            activePicker = picker
            continuation.invokeOnCancellation {
                removeTemporary(temporary.directoryUrl)
                cancel(delegate)
            }
            owner().presentViewController(picker, animated = true, completion = null)
        }
    }

    override suspend fun openDirectory(directoryName: String): CoursewareDirectoryOpenResult {
        if (activeDelegate != null) {
            return CoursewareDirectoryOpenResult.Failed(HomeworkFileGatewayFailure.UNAVAILABLE)
        }
        return suspendCancellableCoroutine { continuation ->
            lateinit var delegate: IosDocumentPickerDelegate
            delegate = IosDocumentPickerDelegate(
                onPicked = { urls ->
                    val selectedDirectory = urls.singleOrNull()
                    if (selectedDirectory == null) {
                        finish(delegate, continuation, CoursewareDirectoryOpenResult.Cancelled)
                    } else {
                        CoroutineScope(continuation.context).launch {
                            var openedSession: CoursewareDirectoryWriteSession? = null
                            var handedOff = false
                            try {
                                val result = withContext(Dispatchers.Default) {
                                    openCoursewareDirectorySession(selectedDirectory, directoryName)
                                }
                                if (result is CoursewareDirectoryOpenResult.Opened) {
                                    openedSession = result.session
                                }
                                withContext(Dispatchers.Main) {
                                    handedOff = finish(delegate, continuation, result)
                                }
                            } finally {
                                if (!handedOff) openedSession?.abort()
                            }
                        }
                    }
                },
                onCancelled = {
                    finish(delegate, continuation, CoursewareDirectoryOpenResult.Cancelled)
                },
            )
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypeFolder),
                asCopy = false,
            ).apply {
                allowsMultipleSelection = false
                this.delegate = delegate
            }
            activeDelegate = delegate
            activePicker = picker
            continuation.invokeOnCancellation {
                cancel(delegate)
            }
            owner().presentViewController(picker, animated = true, completion = null)
        }
    }

    private fun readFiles(urls: List<NSURL>): HomeworkFilePickResult {
        if (urls.isEmpty()) return HomeworkFilePickResult.Cancelled
        return try {
            HomeworkFilePickResult.Selected(
                urls.map { url ->
                    val scoped = url.startAccessingSecurityScopedResource()
                    try {
                        val data = NSData.dataWithContentsOfURL(url)
                            ?: return HomeworkFilePickResult.Failed(HomeworkFileGatewayFailure.IO)
                        val fileName = safeExportFileName(url.lastPathComponent ?: "upload.bin")
                        HomeworkFileContent(
                            fileName = fileName,
                            contentType = fileName.guessContentType(),
                            bytes = data.toByteArray(),
                        )
                    } finally {
                        if (scoped) url.stopAccessingSecurityScopedResource()
                    }
                },
            )
        } catch (_: Exception) {
            HomeworkFilePickResult.Failed(HomeworkFileGatewayFailure.IO)
        }
    }

    private fun <T> finish(
        delegate: IosDocumentPickerDelegate,
        continuation: CancellableContinuation<T>,
        result: T,
    ): Boolean {
        if (activeDelegate !== delegate) return false
        activePicker?.delegate = null
        activePicker = null
        activeDelegate = null
        if (!continuation.isActive) return false
        continuation.resume(result)
        return true
    }

    private fun cancel(delegate: IosDocumentPickerDelegate) {
        if (activeDelegate !== delegate) return
        activePicker?.delegate = null
        activePicker?.dismissViewControllerAnimated(true, completion = null)
        activePicker = null
        activeDelegate = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosDocumentPickerDelegate(
    private val onPicked: (List<NSURL>) -> Unit,
    private val onCancelled: () -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        onPicked(didPickDocumentsAtURLs.filterIsInstance<NSURL>())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onCancelled()
    }
}

@OptIn(ExperimentalForeignApi::class)
private data class TemporaryExport(
    val fileUrl: NSURL,
    val directoryUrl: NSURL,
)

@OptIn(ExperimentalForeignApi::class)
private fun HomeworkFileContent.writeTemporaryExport(): TemporaryExport? {
    val safeName = safeExportFileName(fileName)
    val directoryPath = NSTemporaryDirectory().trimEnd('/') + "/bjtu-homework-" + NSUUID().UUIDString
    val manager = NSFileManager.defaultManager
    if (!manager.createDirectoryAtPath(
            path = directoryPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    ) {
        return null
    }
    val directoryUrl = NSURL(fileURLWithPath = directoryPath, isDirectory = true)
    val fileUrl = NSURL(fileURLWithPath = "$directoryPath/$safeName")
    if (!bytes.toNSData().writeToURL(fileUrl, atomically = true)) {
        removeTemporary(directoryUrl)
        return null
    }
    return TemporaryExport(fileUrl = fileUrl, directoryUrl = directoryUrl)
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun openCoursewareDirectorySession(
    selectedDirectory: NSURL,
    directoryName: String,
): CoursewareDirectoryOpenResult {
    if (!selectedDirectory.startAccessingSecurityScopedResource()) {
        return CoursewareDirectoryOpenResult.Failed(HomeworkFileGatewayFailure.PERMISSION_DENIED)
    }
    val context = currentCoroutineContext()
    var createdRootUrl: NSURL? = null
    return try {
        var result: CoursewareDirectoryOpenResult =
            CoursewareDirectoryOpenResult.Failed(HomeworkFileGatewayFailure.IO)
        NSFileCoordinator().coordinateWritingItemAtURL(
            url = selectedDirectory,
            options = 0uL,
            error = null,
        ) directoryAccess@{ coordinatedDirectory ->
            context.ensureActive()
            val directory = coordinatedDirectory ?: return@directoryAccess
            val candidate = directory.URLByAppendingPathComponent(
                safeExportPathSegment(directoryName),
                isDirectory = true,
            ) ?: return@directoryAccess
            val rootPath = candidate.path ?: return@directoryAccess
            val manager = NSFileManager.defaultManager
            if (manager.fileExistsAtPath(rootPath)) return@directoryAccess
            if (!manager.createDirectoryAtPath(
                    path = rootPath,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null,
                )
            ) {
                return@directoryAccess
            }
            createdRootUrl = candidate
            result = CoursewareDirectoryOpenResult.Opened(
                IosCoursewareDirectoryWriteSession(selectedDirectory, candidate),
            )
        }
        context.ensureActive()
        if (result !is CoursewareDirectoryOpenResult.Opened) {
            selectedDirectory.stopAccessingSecurityScopedResource()
        }
        result
    } catch (error: CancellationException) {
        createdRootUrl?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }
        selectedDirectory.stopAccessingSecurityScopedResource()
        throw error
    } catch (_: Exception) {
        createdRootUrl?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }
        selectedDirectory.stopAccessingSecurityScopedResource()
        CoursewareDirectoryOpenResult.Failed(HomeworkFileGatewayFailure.IO)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosCoursewareDirectoryWriteSession(
    private val selectedDirectory: NSURL,
    private val rootUrl: NSURL,
) : CoursewareDirectoryWriteSession {
    private val lifecycleMutex = Mutex()
    private var closed = false

    override suspend fun write(file: CoursewareDirectoryFile): HomeworkFileSaveResult =
        lifecycleMutex.withLock {
            if (closed) return@withLock HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.UNAVAILABLE)
            withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                try {
                    var result: HomeworkFileSaveResult =
                        HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.IO)
                    NSFileCoordinator().coordinateWritingItemAtURL(
                        url = rootUrl,
                        options = 0uL,
                        error = null,
                    ) fileAccess@{ coordinatedRoot ->
                        context.ensureActive()
                        val root = coordinatedRoot ?: return@fileAccess
                        result = writeCoursewareFile(root, file) { context.ensureActive() }
                    }
                    context.ensureActive()
                    result
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.IO)
                }
            }
        }

    override suspend fun commit(): HomeworkFileSaveResult = lifecycleMutex.withLock {
        if (closed) return@withLock HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.UNAVAILABLE)
        closed = true
        selectedDirectory.stopAccessingSecurityScopedResource()
        HomeworkFileSaveResult.Saved
    }

    override suspend fun abort() {
        withContext(NonCancellable + Dispatchers.Default) {
            lifecycleMutex.withLock {
                if (!closed) {
                    try {
                        NSFileCoordinator().coordinateWritingItemAtURL(
                            url = rootUrl,
                            options = 0uL,
                            error = null,
                        ) { coordinatedRoot ->
                            coordinatedRoot?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }
                        }
                    } finally {
                        closed = true
                        selectedDirectory.stopAccessingSecurityScopedResource()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun writeCoursewareFile(
    rootUrl: NSURL,
    file: CoursewareDirectoryFile,
    ensureActive: () -> Unit,
): HomeworkFileSaveResult {
    ensureActive()
    val manager = NSFileManager.defaultManager
    var folderUrl: NSURL? = rootUrl
    file.relativeFolders.forEach { segment ->
        folderUrl = folderUrl?.URLByAppendingPathComponent(safeExportPathSegment(segment), isDirectory = true)
    }
    val resolvedFolderUrl = folderUrl ?: return HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.IO)
    val folderPath = resolvedFolderUrl.path ?: return HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.IO)
    if (!manager.createDirectoryAtPath(
            path = folderPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    ) {
        return HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.IO)
    }
    val fileUrl = resolvedFolderUrl.URLByAppendingPathComponent(
        safeExportFileName(file.content.fileName),
        isDirectory = false,
    ) ?: return HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.IO)
    val filePath = fileUrl.path ?: return HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.IO)
    if (manager.fileExistsAtPath(filePath)) {
        return HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.IO)
    }
    ensureActive()
    return if (file.content.bytes.toNSData().writeToURL(fileUrl, atomically = true)) {
        HomeworkFileSaveResult.Saved
    } else {
        HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.IO)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun removeTemporary(url: NSURL) {
    NSFileManager.defaultManager.removeItemAtURL(url, error = null)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.convert())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    if (length == 0uL) return byteArrayOf()
    val pointer = bytes?.reinterpret<ByteVar>() ?: return byteArrayOf()
    return pointer.readBytes(length.toInt())
}

private fun String.guessContentType(): String = when (substringAfterLast('.', "").lowercase()) {
    "pdf" -> "application/pdf"
    "doc" -> "application/msword"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "xls" -> "application/vnd.ms-excel"
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "ppt" -> "application/vnd.ms-powerpoint"
    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    "txt" -> "text/plain"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "zip" -> "application/zip"
    else -> "application/octet-stream"
}
