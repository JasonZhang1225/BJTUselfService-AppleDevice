package team.bjtuss.bjtuselfservice.shared.data.course

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport

private const val AA_ORIGIN = "https://aa.bjtu.edu.cn/"
private const val TEACHER_URL =
    "https://aa.bjtu.edu.cn/course_selection/courseselectabsent/absent_list/"
private const val CURRENT_SCHEDULE_URL =
    "https://aa.bjtu.edu.cn/course_selection/courseselect/stuschedule/"
private const val SELECTION_SCHEDULE_URL =
    "https://aa.bjtu.edu.cn/course_selection/courseselecttask/schedule/"
private const val CURRENT_WEEK_URL =
    "https://aa.bjtu.edu.cn/classroom/timeholdresult/room_view/"

data class RemoteCourseScheduleSnapshot(
    val courses: List<Course>,
    val currentWeek: Int,
)

enum class CourseScheduleRemoteFailure {
    NETWORK,
    SESSION_EXPIRED,
    MALFORMED_RESPONSE,
}

class CourseScheduleRemoteException(
    val reason: CourseScheduleRemoteFailure,
) : Exception("Unable to refresh course schedule: ${reason.name}")

interface CourseScheduleRemoteDataSource {
    suspend fun fetchSchedule(): RemoteCourseScheduleSnapshot
}

class SchoolCourseScheduleRemoteDataSource(
    private val transport: SchoolHttpTransport,
    private val requestDelayMillis: Long = 100,
) : CourseScheduleRemoteDataSource {
    override suspend fun fetchSchedule(): RemoteCourseScheduleSnapshot {
        val teacherResponse = request(TEACHER_URL)
        val teachers = when (val parsed = parseTeacherTable(teacherResponse.bodyText())) {
            is TeacherTableParseResult.Failure -> malformed()
            is TeacherTableParseResult.Success -> parsed.teachersByCourse
        }

        val currentResponse = request(CURRENT_SCHEDULE_URL)
        val currentCourses = parseSchedule(
            html = currentResponse.bodyText(),
            isSelectionSchedule = false,
            teachers = teachers,
        )
        val selectionResponse = request(SELECTION_SCHEDULE_URL)
        val selectionCourses = parseSchedule(
            html = selectionResponse.bodyText(),
            isSelectionSchedule = true,
            teachers = teachers,
        )
        val weekResponse = request(CURRENT_WEEK_URL)

        return RemoteCourseScheduleSnapshot(
            courses = (currentCourses + selectionCourses).distinctBy { course ->
                listOf(
                    course.courseId,
                    course.courseLocationIndex.toString(),
                    course.courseTime,
                    course.coursePlace,
                    course.isCurrentSemester.toString(),
                ).joinToString("\u0000")
            },
            currentWeek = parseCurrentWeekFromUrl(weekResponse.finalUrl),
        )
    }

    private suspend fun request(url: String) = try {
        if (requestDelayMillis > 0) delay(requestDelayMillis)
        transport.execute(
            SchoolHttpRequest(
                method = SchoolHttpMethod.GET,
                url = url,
                headers = mapOf("Host" to "aa.bjtu.edu.cn"),
            ),
        ).also { response ->
            if (response.statusCode !in 200..299) {
                throw CourseScheduleRemoteException(CourseScheduleRemoteFailure.NETWORK)
            }
            if (!response.finalUrl.startsWith(AA_ORIGIN)) {
                throw CourseScheduleRemoteException(CourseScheduleRemoteFailure.SESSION_EXPIRED)
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: CourseScheduleRemoteException) {
        throw error
    } catch (_: Exception) {
        throw CourseScheduleRemoteException(CourseScheduleRemoteFailure.NETWORK)
    }

    private fun parseSchedule(
        html: String,
        isSelectionSchedule: Boolean,
        teachers: Map<String, String>,
    ): List<Course> = when (
        val parsed = parseCourseScheduleTable(html, isSelectionSchedule, teachers)
    ) {
        is CourseScheduleTableParseResult.Failure -> malformed()
        is CourseScheduleTableParseResult.Success -> parsed.courses
    }

    private fun malformed(): Nothing =
        throw CourseScheduleRemoteException(CourseScheduleRemoteFailure.MALFORMED_RESPONSE)
}
