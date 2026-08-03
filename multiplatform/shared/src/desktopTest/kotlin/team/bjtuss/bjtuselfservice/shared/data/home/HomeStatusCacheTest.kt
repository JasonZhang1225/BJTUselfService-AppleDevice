package team.bjtuss.bjtuselfservice.shared.data.home

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.cache.db.CacheDatabaseSql
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeStatusCacheTest {
    @Test fun roundTripPreservesDisplayValuesAndAccountScope() {
        val store = inMemoryStore()
        try {
            val local = CacheStoreHomeStatusLocalDataSource(store)
            val status = HomeStatus("2", "19.50", "0.00")
            local.replace("student-a", status)
            assertEquals(status, local.load("student-a"))
            assertNull(local.load("student-b"))
        } finally {
            store.close()
        }
    }

    @Test fun malformedCacheIsIgnored() {
        val store = inMemoryStore()
        try {
            store.putMetadata("student-a", "home_status_v1", "broken")
            assertNull(CacheStoreHomeStatusLocalDataSource(store).load("student-a"))
        } finally {
            store.close()
        }
    }

    private fun inMemoryStore(): CacheStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CacheDatabaseSql.Schema.create(driver).value
        return CacheStore(driver)
    }
}
