package team.bjtuss.bjtuselfservice.shared.data.courseware

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CoursewareTeachingCalendarHtmlParserTest {
    @Test
    fun parsesTeacherAndCalendarFrameWithoutExposingHtml() {
        val teacher = assertIs<CoursewareHtmlParseResult.Success>(
            parseCoursewareTeacherId("""<input id="teacherId" value="T-28">"""),
        )
        val frame = assertIs<CoursewareHtmlParseResult.Success>(
            parseTeachingCalendarFrameUrl(
                """<iframe id="pdfIframe" src="https://bksycenter.bjtu.edu.cn/calendar/1.pdf"></iframe>""",
            ),
        )

        assertEquals("T-28", teacher.value)
        assertEquals("https://bksycenter.bjtu.edu.cn/calendar/1.pdf", frame.value)
    }

    @Test
    fun missingElementsReturnTypedFailure() {
        assertIs<CoursewareHtmlParseResult.Failure>(parseCoursewareTeacherId("<html></html>"))
        assertIs<CoursewareHtmlParseResult.Failure>(parseTeachingCalendarFrameUrl("<html></html>"))
    }
}
