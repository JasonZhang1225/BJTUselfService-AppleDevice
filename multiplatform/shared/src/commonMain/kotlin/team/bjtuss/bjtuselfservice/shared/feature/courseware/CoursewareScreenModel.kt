package team.bjtuss.bjtuselfservice.shared.feature.courseware

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import team.bjtuss.bjtuselfservice.shared.data.courseware.CoursewareOperationResult
import team.bjtuss.bjtuselfservice.shared.data.courseware.CoursewareRefreshResult
import team.bjtuss.bjtuselfservice.shared.data.courseware.CoursewareRepository
import team.bjtuss.bjtuselfservice.shared.data.courseware.CoursewareSyncFailure
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareCourse
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNode
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareSnapshot
import team.bjtuss.bjtuselfservice.shared.domain.courseware.VisibleCoursewareNode
import team.bjtuss.bjtuselfservice.shared.domain.courseware.coursewarePathNames
import team.bjtuss.bjtuselfservice.shared.domain.courseware.findCoursewareNode
import team.bjtuss.bjtuselfservice.shared.domain.courseware.nodesAtCoursewarePath
import team.bjtuss.bjtuselfservice.shared.domain.courseware.visibleCoursewareTree
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent
import team.bjtuss.bjtuselfservice.shared.files.CoursewareDirectoryGateway
import team.bjtuss.bjtuselfservice.shared.files.CoursewareDirectoryFile
import team.bjtuss.bjtuselfservice.shared.files.CoursewareDirectoryOpenResult
import team.bjtuss.bjtuselfservice.shared.files.CoursewareDirectoryWriteSession
import team.bjtuss.bjtuselfservice.shared.files.CoursewareExportNameAllocator
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGatewayFailure
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileSaveResult

enum class CoursewareContentSource {
    CACHE,
    NETWORK,
}

data class CoursewareUiState(
    val courses: List<CoursewareCourse> = emptyList(),
    val selectedCourseId: Int? = null,
    val compactFolderPath: List<String> = emptyList(),
    val expandedFolderKeys: Set<String> = emptySet(),
    val selectedNodeKey: String? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isDownloading: Boolean = false,
    val loadingCourseIds: Set<Int> = emptySet(),
    val loadingFolderKeys: Set<String> = emptySet(),
    val directoryDownloadCompleted: Int = 0,
    val directoryDownloadTotal: Int = 0,
    val source: CoursewareContentSource? = null,
    val failure: CoursewareSyncFailure? = null,
    val fileFailure: CoursewareSyncFailure? = null,
) {
    val selectedCourse: CoursewareCourse?
        get() = courses.firstOrNull { it.id == selectedCourseId }

    val isSelectedCourseLoading: Boolean
        get() = selectedCourseId in loadingCourseIds

    val compactNodes: List<CoursewareNode>
        get() = selectedCourse?.let { nodesAtCoursewarePath(it, compactFolderPath) }.orEmpty()

    val compactPathNames: List<String>
        get() = selectedCourse?.let { coursewarePathNames(it, compactFolderPath) }.orEmpty()

    val visibleTree: List<VisibleCoursewareNode>
        get() = selectedCourse?.let { visibleCoursewareTree(it.children, expandedFolderKeys) }.orEmpty()

    val selectedNode: CoursewareNode?
        get() = selectedCourse?.let { findCoursewareNode(it.children, selectedNodeKey.orEmpty()) }
}

class CoursewareScreenModel(
    private val repository: CoursewareRepository,
) {
    private val mutableState = MutableStateFlow(CoursewareUiState())
    val state: StateFlow<CoursewareUiState> = mutableState.asStateFlow()

    private val refreshMutex = Mutex()
    private val operationMutex = Mutex()
    private val freshCourseIds = mutableSetOf<Int>()
    private var initialized = false

    suspend fun initialize() {
        if (initialized) return
        initialized = true
        val cached = runCatching(repository::load).getOrNull()
        if (cached != null) {
            applySnapshot(
                snapshot = cached,
                source = if (cached.courses.isEmpty()) null else CoursewareContentSource.CACHE,
                failure = null,
            )
        } else {
            mutableState.value = mutableState.value.copy(
                isLoading = true,
                failure = CoursewareSyncFailure.CACHE,
            )
        }
        refresh()
    }

    suspend fun refresh() {
        if (!refreshMutex.tryLock()) return
        try {
            operationMutex.withLock {
                val before = mutableState.value
                mutableState.value = before.copy(
                    isLoading = before.courses.isEmpty(),
                    isRefreshing = before.courses.isNotEmpty(),
                    failure = null,
                )
                when (val result = repository.refresh()) {
                    is CoursewareRefreshResult.Success -> {
                        freshCourseIds.clear()
                        applySnapshot(result.snapshot, CoursewareContentSource.NETWORK, null)
                        mutableState.value.selectedCourseId?.let { loadCourseLocked(it) }
                    }
                    is CoursewareRefreshResult.Failure -> applySnapshot(
                        result.snapshot,
                        if (result.snapshot.courses.isEmpty()) null else CoursewareContentSource.CACHE,
                        result.reason,
                    )
                }
            }
        } finally {
            refreshMutex.unlock()
        }
    }

    suspend fun selectCourse(courseId: Int) = operationMutex.withLock {
        if (mutableState.value.courses.none { it.id == courseId }) return@withLock
        mutableState.value = mutableState.value.copy(
            selectedCourseId = courseId,
            compactFolderPath = emptyList(),
            expandedFolderKeys = emptySet(),
            selectedNodeKey = null,
            fileFailure = null,
        )
        loadCourseLocked(courseId)
    }

    suspend fun openCompactNode(stableKey: String) {
        var state = mutableState.value
        val node = state.compactNodes.firstOrNull { it.stableKey == stableKey } ?: return
        if (node.isFolder && !node.childrenLoaded && !loadFolder(stableKey)) return
        state = mutableState.value
        mutableState.value = if (node.isFolder) {
            state.copy(
                compactFolderPath = state.compactFolderPath + node.stableKey,
                selectedNodeKey = null,
                fileFailure = null,
            )
        } else {
            state.copy(selectedNodeKey = node.stableKey, fileFailure = null)
        }
    }

    fun navigateCompactBack(): Boolean {
        val state = mutableState.value
        if (state.compactFolderPath.isEmpty()) return false
        mutableState.value = state.copy(
            compactFolderPath = state.compactFolderPath.dropLast(1),
            selectedNodeKey = null,
            fileFailure = null,
        )
        return true
    }

    suspend fun toggleExpanded(stableKey: String) {
        var state = mutableState.value
        val course = state.selectedCourse ?: return
        val node = findCoursewareNode(course.children, stableKey) ?: return
        if (!node.isFolder) return
        if (stableKey !in state.expandedFolderKeys && !node.childrenLoaded && !loadFolder(stableKey)) return
        state = mutableState.value
        val expanded = state.expandedFolderKeys.toMutableSet().apply {
            if (!add(stableKey)) remove(stableKey)
        }
        mutableState.value = state.copy(
            expandedFolderKeys = expanded,
            selectedNodeKey = stableKey,
            fileFailure = null,
        )
    }

    fun selectNode(stableKey: String) {
        val course = mutableState.value.selectedCourse ?: return
        if (stableKey.isBlank()) {
            mutableState.value = mutableState.value.copy(selectedNodeKey = null, fileFailure = null)
            return
        }
        if (findCoursewareNode(course.children, stableKey) == null) return
        mutableState.value = mutableState.value.copy(selectedNodeKey = stableKey, fileFailure = null)
    }

    suspend fun downloadResource(
        stableKey: String,
    ): CoursewareOperationResult<HomeworkFileContent> = operationMutex.withLock {
        val course = mutableState.value.selectedCourse
            ?: return@withLock CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
        val node = findCoursewareNode(course.children, stableKey)
            ?.takeIf { !it.isFolder }
            ?: return@withLock CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
        mutableState.value = mutableState.value.copy(
            isDownloading = true,
            selectedNodeKey = node.stableKey,
            fileFailure = null,
        )
        try {
            repository.downloadResource(node).also { result ->
                if (result is CoursewareOperationResult.Failure) {
                    mutableState.value = mutableState.value.copy(fileFailure = result.reason)
                }
            }
        } finally {
            mutableState.value = mutableState.value.copy(isDownloading = false)
        }
    }

    suspend fun exportDirectory(
        stableKey: String?,
        directoryName: String,
        gateway: CoursewareDirectoryGateway,
    ): CoursewareOperationResult<HomeworkFileSaveResult> = operationMutex.withLock {
        val hydrationFailure = hydrateExportTree(stableKey)
        if (hydrationFailure != null) return@withLock CoursewareOperationResult.Failure(hydrationFailure)
        val course = mutableState.value.selectedCourse
            ?: return@withLock CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
        val resources = course.resourcesWithFolders(stableKey)
        if (resources.isEmpty()) {
            return@withLock CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
        }
        mutableState.value = mutableState.value.copy(
            isDownloading = true,
            directoryDownloadCompleted = 0,
            directoryDownloadTotal = resources.size,
            fileFailure = null,
        )
        try {
            val opened = try {
                gateway.openDirectory(directoryName)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                CoursewareDirectoryOpenResult.Failed(HomeworkFileGatewayFailure.IO)
            }
            when (opened) {
                CoursewareDirectoryOpenResult.Cancelled ->
                    CoursewareOperationResult.Success(HomeworkFileSaveResult.Cancelled)
                is CoursewareDirectoryOpenResult.Failed ->
                    CoursewareOperationResult.Success(HomeworkFileSaveResult.Failed(opened.reason))
                is CoursewareDirectoryOpenResult.Opened ->
                    exportResources(resources, opened.session)
            }
        } finally {
            mutableState.value = mutableState.value.copy(
                isDownloading = false,
                directoryDownloadCompleted = 0,
                directoryDownloadTotal = 0,
            )
        }
    }

    private suspend fun exportResources(
        resources: List<CoursewareResourcePath>,
        session: CoursewareDirectoryWriteSession,
    ): CoursewareOperationResult<HomeworkFileSaveResult> {
        var committed = false
        val nameAllocator = CoursewareExportNameAllocator()
        return try {
            for ((index, item) in resources.withIndex()) {
                val downloaded = when (val result = repository.downloadResource(item.node)) {
                    is CoursewareOperationResult.Failure -> {
                        mutableState.value = mutableState.value.copy(fileFailure = result.reason)
                        return result
                    }
                    is CoursewareOperationResult.Success -> result.value
                }
                val exportFile = nameAllocator.resolve(CoursewareDirectoryFile(item.folders, downloaded))
                when (val written = session.write(exportFile)) {
                    HomeworkFileSaveResult.Saved -> {
                        mutableState.value = mutableState.value.copy(directoryDownloadCompleted = index + 1)
                    }
                    else -> return CoursewareOperationResult.Success(written)
                }
            }
            val result = session.commit()
            committed = result == HomeworkFileSaveResult.Saved
            CoursewareOperationResult.Success(result)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            CoursewareOperationResult.Success(
                HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.IO),
            )
        } finally {
            if (!committed) {
                withContext(NonCancellable) { runCatching { session.abort() } }
            }
        }
    }

    suspend fun downloadTeachingCalendar(): CoursewareOperationResult<HomeworkFileContent> = operationMutex.withLock {
        val course = mutableState.value.selectedCourse
            ?: return@withLock CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
        mutableState.value = mutableState.value.copy(isDownloading = true, fileFailure = null)
        try {
            repository.downloadTeachingCalendar(course).also { result ->
                if (result is CoursewareOperationResult.Failure) {
                    mutableState.value = mutableState.value.copy(fileFailure = result.reason)
                }
            }
        } finally {
            mutableState.value = mutableState.value.copy(isDownloading = false)
        }
    }

    fun resourcesUnder(stableKey: String?): List<CoursewareNode> {
        val course = mutableState.value.selectedCourse ?: return emptyList()
        val roots = if (stableKey == null) {
            course.children
        } else {
            val node = findCoursewareNode(course.children, stableKey) ?: return emptyList()
            if (node.isFolder) node.children else listOf(node)
        }
        return roots.flatMapResources()
    }

    fun dismissFailure() {
        mutableState.value = mutableState.value.copy(failure = null)
    }

    fun dismissFileFailure() {
        mutableState.value = mutableState.value.copy(fileFailure = null)
    }

    private suspend fun loadFolder(stableKey: String): Boolean = operationMutex.withLock {
        loadFolderLocked(stableKey)
    }

    private suspend fun loadFolderLocked(stableKey: String): Boolean {
        var before = mutableState.value
        var course = before.selectedCourse ?: return false
        if (!course.childrenLoaded) {
            if (!loadCourseLocked(course.id)) return false
            before = mutableState.value
            course = before.selectedCourse ?: return false
        }
        val folder = findCoursewareNode(course.children, stableKey)
            ?.takeIf(CoursewareNode::isFolder)
            ?: return false
        if (folder.childrenLoaded) return true
        mutableState.value = before.copy(
            loadingFolderKeys = before.loadingFolderKeys + stableKey,
            fileFailure = null,
        )
        return try {
            when (
                val result = repository.loadFolder(
                    snapshot = CoursewareSnapshot(mutableState.value.courses),
                    courseId = course.id,
                    folderKey = stableKey,
                )
            ) {
                is CoursewareOperationResult.Success -> {
                    applySnapshot(result.value, CoursewareContentSource.NETWORK, mutableState.value.failure)
                    true
                }
                is CoursewareOperationResult.Failure -> {
                    mutableState.value = mutableState.value.copy(fileFailure = result.reason)
                    false
                }
            }
        } finally {
            mutableState.value = mutableState.value.copy(
                loadingFolderKeys = mutableState.value.loadingFolderKeys - stableKey,
            )
        }
    }

    private suspend fun hydrateExportTree(stableKey: String?): CoursewareSyncFailure? {
        val selectedCourseId = mutableState.value.selectedCourseId
            ?: return CoursewareSyncFailure.MALFORMED_RESPONSE
        if (!loadCourseLocked(selectedCourseId)) {
            return mutableState.value.fileFailure ?: CoursewareSyncFailure.NETWORK
        }
        val visited = mutableSetOf<String>()
        while (true) {
            val course = mutableState.value.selectedCourse
                ?: return CoursewareSyncFailure.MALFORMED_RESPONSE
            val roots = if (stableKey == null) {
                course.children
            } else {
                val selected = findCoursewareNode(course.children, stableKey)
                    ?: return CoursewareSyncFailure.MALFORMED_RESPONSE
                listOf(selected)
            }
            val unloaded = roots.firstUnloadedFolder() ?: return null
            if (!visited.add(unloaded.stableKey)) return CoursewareSyncFailure.MALFORMED_RESPONSE
            if (!loadFolderLocked(unloaded.stableKey)) {
                return mutableState.value.fileFailure ?: CoursewareSyncFailure.NETWORK
            }
        }
    }

    private suspend fun loadCourseLocked(courseId: Int): Boolean {
        val before = mutableState.value
        val course = before.courses.firstOrNull { it.id == courseId } ?: return false
        if (courseId in freshCourseIds && course.childrenLoaded) return true
        mutableState.value = before.copy(
            loadingCourseIds = before.loadingCourseIds + courseId,
            fileFailure = null,
        )
        return try {
            when (
                val result = repository.loadCourse(
                    snapshot = CoursewareSnapshot(mutableState.value.courses),
                    courseId = courseId,
                )
            ) {
                is CoursewareOperationResult.Success -> {
                    freshCourseIds += courseId
                    applySnapshot(result.value, CoursewareContentSource.NETWORK, mutableState.value.failure)
                    true
                }
                is CoursewareOperationResult.Failure -> {
                    mutableState.value = mutableState.value.copy(fileFailure = result.reason)
                    false
                }
            }
        } finally {
            mutableState.value = mutableState.value.copy(
                loadingCourseIds = mutableState.value.loadingCourseIds - courseId,
            )
        }
    }

    private fun applySnapshot(
        snapshot: CoursewareSnapshot,
        source: CoursewareContentSource?,
        failure: CoursewareSyncFailure?,
    ) {
        val current = mutableState.value
        val selectedCourseId = current.selectedCourseId
            ?.takeIf { id -> snapshot.courses.any { it.id == id } }
            ?: snapshot.courses.firstOrNull()?.id
        val selectedCourse = snapshot.courses.firstOrNull { it.id == selectedCourseId }
        val allKeys = selectedCourse?.children?.allNodeKeys()
        val validPath = selectedCourse?.let { nodesAtCoursewarePath(it, current.compactFolderPath) } != null
        mutableState.value = current.copy(
            courses = snapshot.courses,
            selectedCourseId = selectedCourseId,
            compactFolderPath = current.compactFolderPath.takeIf { validPath }.orEmpty(),
            expandedFolderKeys = current.expandedFolderKeys.filterTo(mutableSetOf()) { it in allKeys.orEmpty() },
            selectedNodeKey = current.selectedNodeKey?.takeIf { it in allKeys.orEmpty() },
            loadingCourseIds = current.loadingCourseIds.filterTo(mutableSetOf()) { id ->
                snapshot.courses.any { it.id == id }
            },
            loadingFolderKeys = current.loadingFolderKeys.filterTo(mutableSetOf()) { it in allKeys.orEmpty() },
            isLoading = false,
            isRefreshing = false,
            source = source,
            failure = failure,
        )
    }
}

private fun List<CoursewareNode>.firstUnloadedFolder(): CoursewareNode? {
    for (node in this) {
        if (node.isFolder && !node.childrenLoaded) return node
        node.children.firstUnloadedFolder()?.let { return it }
    }
    return null
}

private fun List<CoursewareNode>.allNodeKeys(): Set<String> = buildSet {
    fun addLevel(nodes: List<CoursewareNode>) {
        nodes.forEach { node ->
            add(node.stableKey)
            addLevel(node.children)
        }
    }
    addLevel(this@allNodeKeys)
}

private fun List<CoursewareNode>.flatMapResources(): List<CoursewareNode> = buildList {
    fun addLevel(nodes: List<CoursewareNode>) {
        nodes.forEach { node ->
            if (node.isFolder) addLevel(node.children) else add(node)
        }
    }
    addLevel(this@flatMapResources)
}

private data class CoursewareResourcePath(
    val node: CoursewareNode,
    val folders: List<String>,
)

private fun CoursewareCourse.resourcesWithFolders(stableKey: String?): List<CoursewareResourcePath> {
    val root = if (stableKey == null) {
        children
    } else {
        val node = findCoursewareNode(children, stableKey) ?: return emptyList()
        if (node.isFolder) node.children else listOf(node)
    }
    return buildList {
        fun addLevel(nodes: List<CoursewareNode>, folders: List<String>) {
            nodes.forEach { node ->
                if (node.isFolder) {
                    addLevel(node.children, folders + node.name)
                } else {
                    add(CoursewareResourcePath(node, folders))
                }
            }
        }
        addLevel(root, emptyList())
    }
}
