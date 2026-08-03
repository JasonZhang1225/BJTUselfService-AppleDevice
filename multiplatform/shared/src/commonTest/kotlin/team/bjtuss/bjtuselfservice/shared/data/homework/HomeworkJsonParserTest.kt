package team.bjtuss.bjtuselfservice.shared.data.homework

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HomeworkJsonParserTest {
    @Test
    fun parsesSemesterCoursesAndAllHomeworkFields() {
        val semester = assertIs<HomeworkJsonParseResult.Success<String>>(
            parseCurrentSemesterCode("""{"STATUS":"0","result":[{"xqCode":"2026-1"}]}"""),
        )
        val courses = assertIs<HomeworkJsonParseResult.Success<List<SmartCourse>>>(
            parseSmartCourses(
                """{"STATUS":"0","courseList":[{"id":17,"name":"程序设计","teacher_id":28,"course_num":"CS101","fz_id":"G1","xq_code":"2026-1"}]}""",
            ),
        )
        val homework = assertIs<HomeworkJsonParseResult.Success<*>>(
            parseHomeworkList(
                """{
                    "STATUS":"0",
                    "courseNoteList":[{
                      "id":91,"snId":92,"stu_score":"95","user_id":7,
                      "course_id":17,"course_name":"程序设计","title":"第 1 次作业",
                      "content":"<p>要求</p>","create_date":"2026-07-01 08:00",
                      "end_time":"2026-08-01 20:00","open_date":"2026-07-01 09:00",
                      "status":1,"submitCount":20,"allCount":30,"subStatus":"已提交","scoreId":3
                    }]
                }""".trimIndent(),
                homeworkType = 2,
            ),
        ).value as List<*>

        assertEquals("2026-1", semester.value)
        assertEquals(
            SmartCourse(17, "程序设计", 28, "CS101", "G1", "2026-1"),
            courses.value.single(),
        )
        val item = homework.single() as team.bjtuss.bjtuselfservice.shared.domain.homework.Homework
        assertEquals(91, item.upId)
        assertEquals(92, item.idSnId)
        assertEquals("95", item.score)
        assertEquals(2, item.homeworkType)
    }

    @Test
    fun acceptsBlankArraysAndParsesAttachmentMetadata() {
        val empty = assertIs<HomeworkJsonParseResult.Success<*>>(
            parseHomeworkList("""{"STATUS":"0","courseNoteList":""}""", 0),
        ).value as List<*>
        val detail = assertIs<HomeworkJsonParseResult.Success<*>>(
            parseHomeworkDetail(
                """{
                  "STATUS":"0","homeWork":{"content":"<p>请完成实验</p>"},
                  "picList":[{"id":8,"file_name":"实验+模板.docx","pic_size":2048,"url":"private/path"}]
                }""".trimIndent(),
                fallbackContent = "fallback",
            ),
        ).value as team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkDetail

        assertEquals(emptyList<Any?>(), empty)
        assertEquals("<p>请完成实验</p>", detail.content)
        assertEquals("实验 模板.docx", detail.attachments.single().fileName)
        assertEquals(2048L, detail.attachments.single().sizeBytes)
    }

    @Test
    fun rejectsMissingIdentityWithoutLeakingBody() {
        val result = assertIs<HomeworkJsonParseResult.Failure>(
            parseHomeworkList(
                """{"STATUS":"0","courseNoteList":[{"id":1,"course_id":2,"title":"敏感标题"}]}""",
                0,
            ),
        )

        assertEquals("courseNoteList[0].identity", result.field)
        kotlin.test.assertFalse("敏感标题" in result.toString())
    }

    @Test
    fun parsesUploadReceiptWithExtensionlessFile() {
        val receipt = assertIs<HomeworkJsonParseResult.Success<HomeworkUploadReceipt>>(
            parseHomeworkUploadReceipt(
                """{"fileNameNoExt":"README","fileExtName":"","fileSize":"12","visitName":"server-token"}""",
            ),
        ).value

        assertEquals("README", receipt.fileNameNoExt)
        assertEquals("", receipt.fileExtName)
        assertEquals("12", receipt.fileSize)
    }

    @Test
    fun treatsStatus2WithoutListAsEmptyHomeworkNotFailure() {
        // 服务器对“该课程当前类型没有作业”返回 STATUS="2" + message="没有数据"，
        // 原 Android 以默认值容错得到空列表；严格解析必须同样放行，避免整批中断。
        val result = assertIs<HomeworkJsonParseResult.Success<List<*>>>(
            parseHomeworkList(
                """{"page":1,"size":100,"currentRow":0,"total":0,"totalPage":0,"STATUS":"2","message":"没有数据"}""",
                0,
            ),
        ).value

        assertEquals(emptyList<Any?>(), result)
    }

    @Test
    fun stillRejectsOtherNonSuccessStatus() {
        val result = assertIs<HomeworkJsonParseResult.Failure>(
            parseHomeworkList("""{"STATUS":"5","message":"系统异常"}""", 0),
        )

        assertEquals("STATUS", result.field)
    }
}
