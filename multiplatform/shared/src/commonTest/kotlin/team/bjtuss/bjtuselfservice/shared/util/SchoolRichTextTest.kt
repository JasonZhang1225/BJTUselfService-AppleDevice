package team.bjtuss.bjtuselfservice.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchoolRichTextTest {
    @Test
    fun preservesHtmlBreaks() {
        val text = schoolRichTextToPlainMultiline("第一行<br>第二行<br/>第三行")
        assertEquals(listOf("第一行", "第二行", "第三行"), text.lines())
    }

    @Test
    fun breaksNumberedPlainTextItems() {
        val flat =
            "1. 实验名称 课程助教。 2. 实验目的 本课程。 3. 实验环境 Windows。 4. 核心任务 每组。"
        val text = schoolRichTextToPlainMultiline(flat)
        assertTrue(text.lines().size >= 4)
        assertTrue(text.lines()[0].startsWith("1."))
        assertTrue(text.lines().any { it.startsWith("2.") })
        assertTrue(text.lines().any { it.startsWith("3.") })
        assertTrue(text.lines().any { it.startsWith("4.") })
    }

    @Test
    fun stripsTagsButKeepsParagraphs() {
        val text = schoolRichTextToPlainMultiline("<p>段一</p><p>段二</p>")
        assertEquals(listOf("段一", "段二"), text.lines())
    }
}
