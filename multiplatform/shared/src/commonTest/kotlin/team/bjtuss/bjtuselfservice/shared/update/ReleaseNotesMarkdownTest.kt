package team.bjtuss.bjtuselfservice.shared.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReleaseNotesMarkdownTest {
    @Test
    fun parsesHeadingsListsAndTablesFromReleaseNotes() {
        val blocks = parseReleaseNotes(
            """
            ### 热修

            - 新装课表全灰
            - 成绩有更新却不弹窗

            ### 安装说明

            | 文件 | 说明 |
            | --- | --- |
            | `app.apk` | Android debug |
            | `app.ipa` | iOS 未签名 |
            """.trimIndent(),
        )

        val heading = assertIs<ReleaseNoteBlock.Heading>(blocks[0])
        assertEquals(3, heading.level)
        assertEquals("热修", heading.text)

        val list = assertIs<ReleaseNoteBlock.ListItems>(blocks[1])
        assertEquals(listOf("新装课表全灰", "成绩有更新却不弹窗"), list.items)

        assertEquals("安装说明", assertIs<ReleaseNoteBlock.Heading>(blocks[2]).text)

        val table = assertIs<ReleaseNoteBlock.Table>(blocks[3])
        assertEquals(listOf("文件", "说明"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals("`app.apk`", table.rows[0][0])
        assertEquals("Android debug", table.rows[0][1])
    }

    @Test
    fun inlineCodeAndBoldSurviveAnnotation() {
        val annotated = annotatedInlineMarkdown("安装 `1.7.2-KMP-A` 并 **前往下载**")
        assertEquals("安装 1.7.2-KMP-A 并 前往下载", annotated.text)
        assertTrue(annotated.spanStyles.any { it.item.fontFamily != null })
        assertTrue(annotated.spanStyles.any { it.item.fontWeight != null })
    }
}
