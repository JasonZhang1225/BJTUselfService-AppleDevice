package team.bjtuss.bjtuselfservice.shared.cache

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import team.bjtuss.bjtuselfservice.shared.cache.db.CacheDatabaseSql
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import team.bjtuss.bjtuselfservice.shared.domain.grade.GradeSelectionRecord
import team.bjtuss.bjtuselfservice.shared.domain.homework.Homework

class CacheStoreTest {
    @Test
    fun roundTripIsAccountScopedAndClearAccountKeepsGlobalPreferences() {
        val store = inMemoryStore()
        try {
            store.replaceGrades("student-a", listOf(sampleGrade("A")))
            store.replaceCourses("student-a", listOf(sampleCourse("A")))
            store.replaceExams("student-a", listOf(sampleExam("A")))
            store.replaceHomework("student-a", listOf(sampleHomework("A")))
            store.replaceGradeSelections("student-a", listOf(sampleSelection("A")))
            store.putMetadata("student-a", "grades_updated_at", "2026-07-30T12:00:00")

            store.replaceGrades("student-b", listOf(sampleGrade("B")))
            store.savePreferences(
                AppPreferences(
                    autoSyncGrades = true,
                    autoSyncHomework = true,
                    currentWeek = 14,
                    checkUpdate = false,
                    dynamicColor = false,
                    theme = "Dark",
                ),
            )

            assertEquals(sampleGrade("A"), store.grades("student-a").single().copy(id = 0))
            assertEquals(sampleCourse("A"), store.courses("student-a").single().copy(id = 0))
            assertEquals(sampleExam("A"), store.exams("student-a").single().copy(id = 0))
            assertEquals(sampleHomework("A"), store.homework("student-a").single().copy(id = 0))
            assertEquals(listOf(sampleSelection("A")), store.gradeSelections("student-a"))
            assertEquals("2026-07-30T12:00:00", store.metadata("student-a", "grades_updated_at"))
            assertEquals("课程-B", store.grades("student-b").single().courseName)

            val preferences = store.preferences()
            assertTrue(preferences.autoSyncGrades)
            assertTrue(preferences.autoSyncHomework)
            assertEquals(14, preferences.currentWeek)
            assertFalse(preferences.checkUpdate)
            assertFalse(preferences.dynamicColor)
            assertEquals("Dark", preferences.theme)

            store.clearAccount("student-a")

            assertTrue(store.grades("student-a").isEmpty())
            assertTrue(store.courses("student-a").isEmpty())
            assertTrue(store.exams("student-a").isEmpty())
            assertTrue(store.homework("student-a").isEmpty())
            assertTrue(store.gradeSelections("student-a").isEmpty())
            assertNull(store.metadata("student-a", "grades_updated_at"))
            assertEquals("课程-B", store.grades("student-b").single().courseName)
            assertEquals("Dark", store.preferences().theme)
        } finally {
            store.close()
        }
    }

    @Test
    fun fileDatabaseRestoresAfterCloseAndReopen() = withTemporaryDirectory { directory ->
        val first = createDesktopCacheStore(directory)
        assertEquals(CacheOpenState.OPENED, first.state)
        first.store.replaceGrades("student-a", listOf(sampleGrade("restart")))
        first.store.putSetting("ordinary-setting", "kept")
        first.store.close()

        val reopened = createDesktopCacheStore(directory)
        try {
            assertEquals(CacheOpenState.OPENED, reopened.state)
            assertEquals("课程-restart", reopened.store.grades("student-a").single().courseName)
            assertEquals("kept", reopened.store.setting("ordinary-setting"))
        } finally {
            reopened.store.close()
        }
    }

    @Test
    fun versionOneDatabaseMigratesAndLegacyRowsCanBeClaimed() =
        withTemporaryDirectory { directory ->
            val databaseFile = File(directory, "bjtuselfservice_cache.db")
            val legacyDriver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}")
            createVersionOneSchema(legacyDriver)
            legacyDriver.execute(
                identifier = null,
                sql = """
                    INSERT INTO grade_cache (
                        course_name, course_teacher, course_score, course_credits,
                        course_year, semester, detail
                    ) VALUES ('旧课程', '旧教师', '90', '2.0', '2025-2026', '1', '')
                """.trimIndent(),
                parameters = 0,
            ).value
            legacyDriver.execute(null, "PRAGMA user_version = 1", 0).value
            legacyDriver.close()

            val migrated = createDesktopCacheStore(directory)
            try {
                assertEquals(CacheOpenState.OPENED, migrated.state)
                assertTrue(migrated.store.grades("student-a").isEmpty())

                migrated.store.claimLegacyAccountData("student-a")

                assertEquals("旧课程", migrated.store.grades("student-a").single().courseName)
                assertEquals(CacheDatabaseSql.Schema.version, 2L)
            } finally {
                migrated.store.close()
            }
        }

    @Test
    fun corruptedFileIsDeletedAndRecreatedOnce() = withTemporaryDirectory { directory ->
        val databaseFile = File(directory, "bjtuselfservice_cache.db")
        databaseFile.writeText("not-a-sqlite-database")

        val recovered = createDesktopCacheStore(directory)
        try {
            assertEquals(CacheOpenState.RECOVERED_AFTER_RESET, recovered.state)
            assertEquals(0L, recovered.store.rowCount())
            assertNotEquals("not-a-sqlite-database", databaseFile.readText())
        } finally {
            recovered.store.close()
        }
    }

    @Test
    fun invalidScopeAndCorruptPreferenceFallbackAreSafe() {
        val store = inMemoryStore()
        try {
            store.putSetting("auto_sync_grades", "unexpected")
            store.putSetting("current_week", "999")
            store.putSetting("theme", "")

            val preferences = store.preferences()
            assertTrue(preferences.autoSyncGrades)
            assertEquals(56, preferences.currentWeek)
            assertEquals("System", preferences.theme)
        } finally {
            store.close()
        }
    }

    @Test
    fun gradeSnapshotReplacesGradesAndSelectionsTogether() {
        val store = inMemoryStore()
        try {
            store.replaceGradeSnapshot(
                accountScope = "student-a",
                grades = listOf(sampleGrade("snapshot")),
                selections = listOf(sampleSelection("snapshot")),
            )

            assertEquals("课程-snapshot", store.grades("student-a").single().courseName)
            assertEquals(
                listOf(sampleSelection("snapshot")),
                store.gradeSelections("student-a"),
            )
        } finally {
            store.close()
        }
    }

    @Test
    fun courseSnapshotKeepsCoursesAndCurrentWeekAccountScoped() {
        val store = inMemoryStore()
        try {
            store.replaceCourseSnapshot("student-a", listOf(sampleCourse("A")), 8)
            store.replaceCourseSnapshot("student-b", listOf(sampleCourse("B")), 12)

            assertEquals("课程-A", store.courses("student-a").single().courseName)
            assertEquals(8, store.courseCurrentWeek("student-a"))
            assertEquals("课程-B", store.courses("student-b").single().courseName)
            assertEquals(12, store.courseCurrentWeek("student-b"))
        } finally {
            store.close()
        }
    }

    private fun inMemoryStore(): CacheStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CacheDatabaseSql.Schema.create(driver).value
        return CacheStore(driver)
    }

    private fun createVersionOneSchema(driver: SqlDriver) {
        listOf(
            """
                CREATE TABLE grade_cache (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    course_name TEXT NOT NULL,
                    course_teacher TEXT NOT NULL,
                    course_score TEXT NOT NULL,
                    course_credits TEXT NOT NULL,
                    course_year TEXT NOT NULL,
                    semester TEXT NOT NULL,
                    detail TEXT NOT NULL DEFAULT ''
                )
            """,
            """
                CREATE TABLE course_cache (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    course_id TEXT NOT NULL,
                    course_name TEXT NOT NULL,
                    course_teacher TEXT NOT NULL,
                    course_location_index INTEGER NOT NULL,
                    course_time TEXT NOT NULL,
                    course_place TEXT NOT NULL,
                    is_current_semester INTEGER NOT NULL
                )
            """,
            """
                CREATE TABLE exam_cache (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    exam_type TEXT NOT NULL,
                    course_name TEXT NOT NULL,
                    exam_time_and_place TEXT NOT NULL,
                    exam_status TEXT NOT NULL,
                    detail TEXT NOT NULL
                )
            """,
            """
                CREATE TABLE homework_cache (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    up_id INTEGER NOT NULL,
                    id_sn_id INTEGER,
                    score TEXT NOT NULL,
                    user_id INTEGER NOT NULL,
                    course_id INTEGER NOT NULL,
                    course_name TEXT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    create_date TEXT NOT NULL,
                    end_time TEXT NOT NULL,
                    open_date TEXT NOT NULL,
                    status INTEGER NOT NULL,
                    submit_count INTEGER NOT NULL,
                    all_count INTEGER NOT NULL,
                    sub_status TEXT NOT NULL,
                    score_id INTEGER NOT NULL,
                    homework_type INTEGER NOT NULL
                )
            """,
        ).forEach { sql -> driver.execute(null, sql.trimIndent(), 0).value }
    }

    private inline fun withTemporaryDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("bjtu-cache-test-").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun sampleGrade(suffix: String) = Grade(
        courseName = "课程-$suffix",
        courseTeacher = "教师-$suffix",
        courseScore = "90",
        courseCredits = "2.0",
        courseYear = "2025-2026",
        semester = "1",
        detail = "详情-$suffix",
    )

    private fun sampleCourse(suffix: String) = Course(
        courseId = "course-$suffix",
        courseName = "课程-$suffix",
        courseTeacher = "教师-$suffix",
        courseLocationIndex = 7,
        courseTime = "第1-16周",
        coursePlace = "教室-$suffix",
        isCurrentSemester = true,
    )

    private fun sampleExam(suffix: String) = ExamSchedule(
        examType = "期末考试",
        courseName = "课程-$suffix",
        examTimeAndPlace = "第18周 教室-$suffix",
        examStatus = "正常",
        detail = "详情-$suffix",
    )

    private fun sampleHomework(suffix: String) = Homework(
        upId = 1,
        idSnId = 2,
        score = "未评分",
        userId = 3,
        courseId = 4,
        courseName = "课程-$suffix",
        title = "作业-$suffix",
        content = "内容-$suffix",
        createDate = "2026-07-01 08:00",
        endTime = "2026-08-01 08:00",
        openDate = "2026-07-01 08:00",
        status = 1,
        submitCount = 0,
        allCount = 1,
        subStatus = "未提交",
        scoreId = 5,
        homeworkType = 0,
    )

    private fun sampleSelection(suffix: String) = GradeSelectionRecord(
        courseName = "课程-$suffix",
        courseTeacher = "教师-$suffix",
        courseYear = "2025-2026",
        semester = "1",
        lastKnownScore = "90",
        lastKnownCredits = "2.0",
        occurrence = 0,
    )
}
