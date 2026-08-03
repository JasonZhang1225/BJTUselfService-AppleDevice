package team.bjtuss.bjtuselfservice.shared.data.courseware

import kotlinx.coroutines.CancellationException
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNode
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareCourse
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareSnapshot
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNodeKind
import team.bjtuss.bjtuselfservice.shared.domain.courseware.findCoursewareNode
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent

private const val COURSEWARE_CACHE_KEY = "courseware_tree_v1"

enum class CoursewareSyncFailure {
    NETWORK,
    SESSION_EXPIRED,
    MALFORMED_RESPONSE,
    SECURE_CHANNEL_UNAVAILABLE,
    CACHE,
}

sealed interface CoursewareRefreshResult {
    data class Success(val snapshot: CoursewareSnapshot) : CoursewareRefreshResult
    data class Failure(
        val snapshot: CoursewareSnapshot,
        val reason: CoursewareSyncFailure,
    ) : CoursewareRefreshResult
}

sealed interface CoursewareOperationResult<out T> {
    data class Success<T>(val value: T) : CoursewareOperationResult<T>
    data class Failure(val reason: CoursewareSyncFailure) : CoursewareOperationResult<Nothing>
}

interface CoursewareLocalDataSource {
    fun load(accountScope: String): CoursewareSnapshot
    fun replace(accountScope: String, snapshot: CoursewareSnapshot)
}

class CacheStoreCoursewareLocalDataSource(
    private val cacheStore: CacheStore,
) : CoursewareLocalDataSource {
    override fun load(accountScope: String): CoursewareSnapshot {
        val encoded = cacheStore.metadata(accountScope, COURSEWARE_CACHE_KEY)
            ?: return CoursewareSnapshot(emptyList())
        return decodeCoursewareSnapshot(encoded)
            ?: throw IllegalStateException("Invalid courseware cache")
    }

    override fun replace(accountScope: String, snapshot: CoursewareSnapshot) {
        cacheStore.putMetadata(accountScope, COURSEWARE_CACHE_KEY, encodeCoursewareSnapshot(snapshot))
    }
}

interface CoursewareRepository {
    fun load(): CoursewareSnapshot
    suspend fun refresh(): CoursewareRefreshResult
    suspend fun loadCourse(
        snapshot: CoursewareSnapshot,
        courseId: Int,
    ): CoursewareOperationResult<CoursewareSnapshot> =
        CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
    suspend fun loadFolder(
        snapshot: CoursewareSnapshot,
        courseId: Int,
        folderKey: String,
    ): CoursewareOperationResult<CoursewareSnapshot> =
        CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
    suspend fun downloadResource(node: CoursewareNode): CoursewareOperationResult<HomeworkFileContent>
    suspend fun downloadTeachingCalendar(course: CoursewareCourse): CoursewareOperationResult<HomeworkFileContent>
}

class DefaultCoursewareRepository(
    accountScope: String,
    private val local: CoursewareLocalDataSource,
    private val remote: CoursewareRemoteDataSource,
) : CoursewareRepository {
    private val accountScope = accountScope.trim().also {
        require(it.isNotEmpty()) { "accountScope cannot be blank" }
    }

    override fun load(): CoursewareSnapshot = local.load(accountScope)

    override suspend fun refresh(): CoursewareRefreshResult {
        val fallback = runCatching(::load).getOrElse { CoursewareSnapshot(emptyList()) }
        val remoteSnapshot = try {
            remote.fetchSnapshot()
        } catch (error: CancellationException) {
            throw error
        } catch (error: CoursewareRemoteException) {
            return CoursewareRefreshResult.Failure(fallback, error.reason.toSyncFailure())
        } catch (_: Exception) {
            return CoursewareRefreshResult.Failure(fallback, CoursewareSyncFailure.NETWORK)
        }
        val mergedSnapshot = remoteSnapshot.mergeCachedChildren(fallback)
        return try {
            local.replace(accountScope, mergedSnapshot)
            CoursewareRefreshResult.Success(local.load(accountScope))
        } catch (_: Exception) {
            CoursewareRefreshResult.Failure(
                snapshot = runCatching(::load).getOrElse { fallback },
                reason = CoursewareSyncFailure.CACHE,
            )
        }
    }

    override suspend fun loadCourse(
        snapshot: CoursewareSnapshot,
        courseId: Int,
    ): CoursewareOperationResult<CoursewareSnapshot> {
        val course = snapshot.courses.firstOrNull { it.id == courseId }
            ?: return CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
        val children = try {
            remote.fetchChildren(course, parentId = 0)
        } catch (error: CancellationException) {
            throw error
        } catch (error: CoursewareRemoteException) {
            return CoursewareOperationResult.Failure(error.reason.toSyncFailure())
        } catch (_: Exception) {
            return CoursewareOperationResult.Failure(CoursewareSyncFailure.NETWORK)
        }
        if (children.hasDuplicateKeys()) {
            return CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
        }
        val updated = snapshot.copy(
            courses = snapshot.courses.map { candidate ->
                if (candidate.id == courseId) {
                    candidate.copy(children = children, childrenLoaded = true)
                } else {
                    candidate
                }
            },
        )
        return try {
            local.replace(accountScope, updated)
            CoursewareOperationResult.Success(local.load(accountScope))
        } catch (_: Exception) {
            CoursewareOperationResult.Failure(CoursewareSyncFailure.CACHE)
        }
    }

    override suspend fun loadFolder(
        snapshot: CoursewareSnapshot,
        courseId: Int,
        folderKey: String,
    ): CoursewareOperationResult<CoursewareSnapshot> {
        val course = snapshot.courses.firstOrNull { it.id == courseId }
            ?: return CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
        if (!course.childrenLoaded) {
            return CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
        }
        val folder = findCoursewareNode(course.children, folderKey)
            ?.takeIf { it.kind == CoursewareNodeKind.FOLDER }
            ?: return CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
        if (folder.childrenLoaded) return CoursewareOperationResult.Success(snapshot)

        val children = try {
            remote.fetchChildren(course, folder.id)
        } catch (error: CancellationException) {
            throw error
        } catch (error: CoursewareRemoteException) {
            return CoursewareOperationResult.Failure(error.reason.toSyncFailure())
        } catch (_: Exception) {
            return CoursewareOperationResult.Failure(CoursewareSyncFailure.NETWORK)
        }
        val existingKeys = course.children.allNodeKeys() - folder.stableKey
        if (children.any { it.stableKey in existingKeys } || children.hasDuplicateKeys()) {
            return CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
        }
        val updatedChildren = course.children.replaceNode(folderKey) {
            it.copy(children = children, childrenLoaded = true)
        } ?: return CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
        val updated = snapshot.copy(
            courses = snapshot.courses.map { candidate ->
                if (candidate.id == courseId) candidate.copy(children = updatedChildren) else candidate
            },
        )
        return try {
            local.replace(accountScope, updated)
            CoursewareOperationResult.Success(local.load(accountScope))
        } catch (_: Exception) {
            CoursewareOperationResult.Failure(CoursewareSyncFailure.CACHE)
        }
    }

    override suspend fun downloadResource(
        node: CoursewareNode,
    ): CoursewareOperationResult<HomeworkFileContent> = try {
        CoursewareOperationResult.Success(remote.downloadResource(node))
    } catch (error: CancellationException) {
        throw error
    } catch (error: CoursewareRemoteException) {
        CoursewareOperationResult.Failure(error.reason.toSyncFailure())
    } catch (_: Exception) {
        CoursewareOperationResult.Failure(CoursewareSyncFailure.NETWORK)
    }

    override suspend fun downloadTeachingCalendar(
        course: CoursewareCourse,
    ): CoursewareOperationResult<HomeworkFileContent> = try {
        CoursewareOperationResult.Success(remote.downloadTeachingCalendar(course))
    } catch (error: CancellationException) {
        throw error
    } catch (error: CoursewareRemoteException) {
        CoursewareOperationResult.Failure(error.reason.toSyncFailure())
    } catch (_: Exception) {
        CoursewareOperationResult.Failure(CoursewareSyncFailure.NETWORK)
    }
}

private fun CoursewareSnapshot.mergeCachedChildren(cached: CoursewareSnapshot): CoursewareSnapshot {
    val cachedById = cached.courses.associateBy(CoursewareCourse::id)
    return copy(
        courses = courses.map { catalogCourse ->
            val cachedCourse = cachedById[catalogCourse.id]
                ?.takeIf {
                    it.courseNumber == catalogCourse.courseNumber &&
                        it.groupId == catalogCourse.groupId &&
                        it.semesterCode == catalogCourse.semesterCode
                }
            if (cachedCourse == null) {
                catalogCourse
            } else {
                catalogCourse.copy(
                    children = cachedCourse.children,
                    childrenLoaded = cachedCourse.childrenLoaded,
                )
            }
        },
    )
}

private fun List<CoursewareNode>.replaceNode(
    stableKey: String,
    transform: (CoursewareNode) -> CoursewareNode,
): List<CoursewareNode>? {
    var found = false
    fun replaceLevel(nodes: List<CoursewareNode>): List<CoursewareNode> = nodes.map { node ->
        when {
            node.stableKey == stableKey -> {
                found = true
                transform(node)
            }
            found -> node
            else -> node.copy(children = replaceLevel(node.children))
        }
    }
    val updated = replaceLevel(this)
    return updated.takeIf { found }
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

private fun List<CoursewareNode>.hasDuplicateKeys(): Boolean {
    val seen = mutableSetOf<String>()
    fun visit(nodes: List<CoursewareNode>): Boolean = nodes.any { node ->
        !seen.add(node.stableKey) || visit(node.children)
    }
    return visit(this)
}

private fun CoursewareRemoteFailure.toSyncFailure(): CoursewareSyncFailure = when (this) {
    CoursewareRemoteFailure.NETWORK -> CoursewareSyncFailure.NETWORK
    CoursewareRemoteFailure.SESSION_EXPIRED -> CoursewareSyncFailure.SESSION_EXPIRED
    CoursewareRemoteFailure.MALFORMED_RESPONSE -> CoursewareSyncFailure.MALFORMED_RESPONSE
    CoursewareRemoteFailure.SECURE_CHANNEL_UNAVAILABLE -> CoursewareSyncFailure.SECURE_CHANNEL_UNAVAILABLE
}
