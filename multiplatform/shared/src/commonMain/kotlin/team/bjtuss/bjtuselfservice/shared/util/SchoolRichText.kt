package team.bjtuss.bjtuselfservice.shared.util

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

sealed interface SchoolRichTextBlock {
    data class Paragraph(val text: String) : SchoolRichTextBlock
    data class Table(val rows: List<List<String>>) : SchoolRichTextBlock
}

/**
 * 把学校侧可能是 HTML 或纯文本的「长说明」整理成适合 Compose Text 的多行纯文本。
 *
 * - HTML：`<br>` / 块级结束标签 → 换行，再去标签；样式/脚本内容不进入正文
 * - 纯文本：在「1. / 2、 / 1）」等条目序号前断行（老师常写成一整段）
 */
fun schoolRichTextToPlainMultiline(source: String?): String {
    return schoolRichTextToBlocks(source).joinToString("\n") { block ->
        when (block) {
            is SchoolRichTextBlock.Paragraph -> block.text
            is SchoolRichTextBlock.Table -> block.rows.joinToString("\n") { row ->
                row.joinToString("\t")
            }
        }
    }
}

fun schoolRichTextToBlocks(source: String?): List<SchoolRichTextBlock> {
    if (source.isNullOrBlank()) return emptyList()
    val trimmed = source.trim()
    return if (trimmed.contains('<') && trimmed.contains('>')) {
        htmlFragmentToBlocks(trimmed)
    } else {
        normalizeSchoolRichText(trimmed)
            .takeIf(String::isNotBlank)
            ?.let { listOf(SchoolRichTextBlock.Paragraph(it)) }
            .orEmpty()
    }
}

private val blockTags = setOf(
    "address", "article", "aside", "blockquote", "div", "dl", "fieldset", "figcaption",
    "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr",
    "li", "main", "nav", "ol", "p", "pre", "section", "ul",
)

private val ignoredTags = setOf("head", "link", "meta", "noscript", "script", "style", "template", "title")

private fun htmlFragmentToBlocks(html: String): List<SchoolRichTextBlock> {
    val root = Ksoup.parse(html).body()
    val blocks = mutableListOf<SchoolRichTextBlock>()
    val pendingText = StringBuilder()

    fun flushText() {
        normalizeSchoolRichText(pendingText.toString())
            .takeIf(String::isNotBlank)
            ?.let { blocks += SchoolRichTextBlock.Paragraph(it) }
        pendingText.clear()
    }

    fun appendBreak() {
        if (pendingText.isNotEmpty() && pendingText.last() != '\n') pendingText.append('\n')
    }

    fun visit(node: Node) {
        if (node is TextNode) {
            pendingText.append(node.text())
            return
        }
        if (node !is Element) {
            node.childNodes().forEach(::visit)
            return
        }

        val element = node
        val tag = element.tagName().lowercase()
        if (tag in ignoredTags) return
        if (tag == "table") {
            flushText()
            parseRichTextTable(element)?.let { blocks += SchoolRichTextBlock.Table(it) }
                ?: pendingText.append(element.text())
            appendBreak()
            return
        }
        if (tag == "br") {
            appendBreak()
            return
        }

        element.childNodes().forEach(::visit)
        if (tag in blockTags) appendBreak()
    }

    visit(root)
    flushText()
    return blocks
}

private fun parseRichTextTable(table: Element): List<List<String>>? {
    val rows = table.select("tr").mapNotNull { row ->
        val cells = row.children().filter { cell ->
            val tag = cell.tagName().lowercase()
            tag == "td" || tag == "th"
        }
        if (cells.isEmpty()) {
            null
        } else {
            cells.map { cell ->
                normalizeSchoolRichText(cell.text())
            }
        }
    }.filter { row -> row.any(String::isNotBlank) }
    return rows.takeIf { it.isNotEmpty() }
}

private fun normalizeSchoolRichText(value: String): String = breakNumberedItems(value)
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .replace('\u00a0', ' ')
    .lines()
    .map { it.replace(Regex("[ \\t]+"), " ").trim() }
    .filter { it.isNotEmpty() }
    .joinToString("\n")

/**
 * 在「数字 + . / 、 / )」类条目序号前插入换行。
 * 仅当序号前已有非空白字符时插入，避免行首重复空行。
 * 同时覆盖「。3. 实验」这种中文句号后紧跟序号的情况。
 */
private fun breakNumberedItems(text: String): String {
    if (text.isBlank()) return text
    // 中文句号/分号后接序号：优先断行
    var result = text.replace(
        Regex("""([。；;!?？])\s*(?=(\d{1,2})([.、．)]|）))"""),
        "$1\n",
    )
    // 空白后接「1. / 2、 / 3）」且不在行首
    result = result.replace(
        Regex("""(?<=\S)[ \t]+(?=(\d{1,2})([.、．)]|）)\s*\S)"""),
        "\n",
    )
    return result
}
