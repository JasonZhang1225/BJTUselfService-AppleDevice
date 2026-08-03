package team.bjtuss.bjtuselfservice.shared.cache

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import team.bjtuss.bjtuselfservice.shared.cache.db.CacheDatabaseSql

private const val CACHE_DATABASE_FILE_NAME = "bjtuselfservice_cache.db"

@OptIn(ExperimentalForeignApi::class)
fun createIosCacheStore(): CacheStoreHandle {
    val fileManager = NSFileManager.defaultManager
    val directory = requireNotNull(
        fileManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )?.path,
    ) { "无法定位 iOS Application Support 目录。" }
    val possibleDatabasePaths = listOf(
        "$directory/databases/$CACHE_DATABASE_FILE_NAME",
        "$directory/$CACHE_DATABASE_FILE_NAME",
    )

    return openCacheStoreWithRecovery(
        openDriver = {
            NativeSqliteDriver(
                schema = CacheDatabaseSql.Schema,
                name = CACHE_DATABASE_FILE_NAME,
            )
        },
        deleteStorage = {
            possibleDatabasePaths.forEach { databasePath ->
                listOf(databasePath, "$databasePath-wal", "$databasePath-shm").forEach { path ->
                    if (fileManager.fileExistsAtPath(path)) {
                        check(fileManager.removeItemAtPath(path, error = null)) {
                            "无法重建 iOS 本地缓存数据库。"
                        }
                    }
                }
            }
        },
    )
}
