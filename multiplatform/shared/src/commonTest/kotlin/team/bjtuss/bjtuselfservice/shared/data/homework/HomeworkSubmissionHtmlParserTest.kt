package team.bjtuss.bjtuselfservice.shared.data.homework

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HomeworkSubmissionHtmlParserTest {
    @Test
    fun parsesSubmittedAttachmentOnClickArguments() {
        val result = assertIs<SubmittedHomeworkParseResult.Success>(
            parseSubmittedHomeworkAttachments(
                """<div class="homeworkContent" onclick="download('/private/a','实验+报告.pdf','91')"></div>""",
            ),
        )

        assertEquals(1, result.attachments.size)
        assertEquals("91", result.attachments.single().id)
        assertEquals("实验 报告.pdf", result.attachments.single().fileName)
        assertEquals("/private/a", result.attachments.single().sourcePath)
    }

    @Test
    fun serverErrorIsFailureWithoutBodyLeak() {
        val result = parseSubmittedHomeworkAttachments(
            "<html><body>系统发生了未处理的异常：敏感服务端详情</body></html>",
        )

        assertIs<SubmittedHomeworkParseResult.Failure>(result)
        kotlin.test.assertFalse("敏感服务端详情" in result.toString())
    }
}
