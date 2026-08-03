package team.bjtuss.bjtuselfservice.shared.cache

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import team.bjtuss.bjtuselfservice.shared.cache.db.CacheDatabaseSql

private const val CACHE_DATABASE_NAME = "bjtuselfservice_cache.db"

fun createAndroidCacheStore(context: Context): CacheStoreHandle {
    val appContext = context.applicationContext
    return openCacheStoreWithRecovery(
        openDriver = {
            AndroidSqliteDriver(
                schema = CacheDatabaseSql.Schema,
                context = appContext,
                name = CACHE_DATABASE_NAME,
            )
        },
        deleteStorage = {
            appContext.deleteDatabase(CACHE_DATABASE_NAME)
        },
    )
}
