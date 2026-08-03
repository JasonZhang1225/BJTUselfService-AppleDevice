package team.bjtuss.bjtuselfservice.shared.data.home

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.cache.db.CacheDatabaseSql
import team.bjtuss.bjtuselfservice.shared.domain.change.DataChangeKind
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeDomain
import team.bjtuss.bjtuselfservice.shared.domain.home.HomeChangeRecord

class HomeChangeFeedRepositoryTest {
    @Test
    fun firstEmptySnapshotEstablishesBaselineWithoutFloodingThenRecordsLaterAddition() = runBlocking {
        val store = inMemoryStore()
        try {
            val repository = CacheStoreHomeChangeFeedRepository("student-a", store)
            val added = record(HomeChangeDomain.EXAMS, DataChangeKind.ADDED, "高等数学")

            assertTrue(repository.acceptRefresh(HomeChangeDomain.EXAMS, hadPreviousItems = false, listOf(added)))
            assertTrue(repository.records.value.isEmpty())

            val reopened = CacheStoreHomeChangeFeedRepository("student-a", store)
            assertTrue(reopened.acceptRefresh(HomeChangeDomain.EXAMS, hadPreviousItems = false, listOf(added)))
            assertEquals(listOf(added), reopened.records.value)
            assertTrue(CacheStoreHomeChangeFeedRepository("student-b", store).records.value.isEmpty())
        } finally {
            store.close()
        }
    }

    @Test
    fun appendDeduplicatesAndDomainClearPreservesOtherRecords() = runBlocking {
        val store = inMemoryStore()
        try {
            val repository = CacheStoreHomeChangeFeedRepository("student-a", store)
            val grade = record(HomeChangeDomain.GRADES, DataChangeKind.MODIFIED, "高等数学")
            val homework = record(HomeChangeDomain.HOMEWORK, DataChangeKind.DELETED, "实验报告")

            repository.acceptRefresh(HomeChangeDomain.GRADES, hadPreviousItems = true, listOf(grade, grade))
            repository.acceptRefresh(HomeChangeDomain.HOMEWORK, hadPreviousItems = true, listOf(homework))
            assertEquals(listOf(grade, homework), repository.records.value)

            assertTrue(repository.clear(HomeChangeDomain.GRADES))
            assertEquals(listOf(homework), repository.records.value)
            assertTrue(repository.clear())
            assertTrue(repository.records.value.isEmpty())
        } finally {
            store.close()
        }
    }

    private fun record(domain: HomeChangeDomain, kind: DataChangeKind, title: String) =
        HomeChangeRecord(domain, kind, title, beforeDetail = "旧", afterDetail = "新")

    private fun inMemoryStore(): CacheStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CacheDatabaseSql.Schema.create(driver).value
        return CacheStore(driver)
    }
}
