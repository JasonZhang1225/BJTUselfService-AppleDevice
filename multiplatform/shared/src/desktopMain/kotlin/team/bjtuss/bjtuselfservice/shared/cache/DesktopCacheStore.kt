package team.bjtuss.bjtuselfservice.shared.cache

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties
import team.bjtuss.bjtuselfservice.shared.cache.db.CacheDatabaseSql

private const val CACHE_DATABASE_FILE_NAME = "bjtuselfservice_cache.db"

fun createDesktopCacheStore(
    baseDirectory: File = File(
        System.getProperty("user.home"),
        "Library/Application Support/BJTUselfServiceKMP",
    ),
): CacheStoreHandle {
    require(baseDirectory.isDirectory || baseDirectory.mkdirs()) {
        "无法创建本地缓存目录。"
    }
    val databaseFile = File(baseDirectory, CACHE_DATABASE_FILE_NAME)
    return openCacheStoreWithRecovery(
        openDriver = {
            JdbcSqliteDriver(
                url = "jdbc:sqlite:${databaseFile.absolutePath}",
                properties = Properties(),
                schema = CacheDatabaseSql.Schema,
            )
        },
        deleteStorage = {
            listOf(
                databaseFile,
                File("${databaseFile.absolutePath}-wal"),
                File("${databaseFile.absolutePath}-shm"),
            ).forEach { file ->
                if (file.exists() && !file.delete()) {
                    error("无法重建本地缓存数据库。")
                }
            }
        },
    )
}
