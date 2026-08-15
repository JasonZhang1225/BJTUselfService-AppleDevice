package team.bjtuss.bjtuselfservice.shared.update

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

sealed interface ReleaseNoteBlock {
    data class Heading(val level: Int, val text: String) : ReleaseNoteBlock
    data class Paragraph(val text: String) : ReleaseNoteBlock
    data class ListItems(val items: List<String>) : ReleaseNoteBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : ReleaseNoteBlock
}

private val headingPattern = Regex("^(#{1,6})\\s+(.*)$")

fun parseReleaseNotes(markdown: String): List<ReleaseNoteBlock> {
    val lines = markdown.replace("\r\n", "\n").lines()
    val blocks = mutableListOf<ReleaseNoteBlock>()
    var index = 0
    while (index < lines.size) {
        val trimmed = lines[index].trim()
        if (trimmed.isEmpty()) {
            index += 1
            continue
        }
        val heading = headingPattern.matchEntire(trimmed)
        if (heading != null) {
            blocks += ReleaseNoteBlock.Heading(
                level = heading.groupValues[1].length,
                text = heading.groupValues[2].trim(),
            )
            index += 1
            continue
        }
        if (trimmed.startsWith("|")) {
            val tableLines = mutableListOf<String>()
            while (index < lines.size && lines[index].trim().startsWith("|")) {
                tableLines += lines[index].trim()
                index += 1
            }
            parseMarkdownTable(tableLines)?.let { blocks += it }
            continue
        }
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            val items = mutableListOf<String>()
            while (index < lines.size) {
                val item = lines[index].trim()
                if (item.startsWith("- ") || item.startsWith("* ")) {
                    items += item.drop(2).trim()
                    index += 1
                } else {
                    break
                }
            }
            if (items.isNotEmpty()) blocks += ReleaseNoteBlock.ListItems(items)
            continue
        }
        val paragraph = StringBuilder(trimmed)
        index += 1
        while (index < lines.size) {
            val next = lines[index].trim()
            if (next.isEmpty() ||
                next.startsWith("|") ||
                headingPattern.matches(next) ||
                next.startsWith("- ") ||
                next.startsWith("* ")
            ) {
                break
            }
            paragraph.append(' ').append(next)
            index += 1
        }
        blocks += ReleaseNoteBlock.Paragraph(paragraph.toString())
    }
    return blocks
}

internal fun parseMarkdownTable(lines: List<String>): ReleaseNoteBlock.Table? {
    val rows = lines.map { line ->
        line.trim().trim('|').split('|').map { it.trim() }
    }
    val data = rows.filterNot { cells ->
        cells.isNotEmpty() && cells.all { cell -> cell.isEmpty() || cell.all { ch -> ch == '-' || ch == ':' } }
    }
    if (data.isEmpty()) return null
    return ReleaseNoteBlock.Table(headers = data.first(), rows = data.drop(1))
}

fun annotatedInlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", startIndex = index + 2)
                if (end >= 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(text.substring(index + 2, end))
                    }
                    index = end + 2
                } else {
                    append(text[index])
                    index += 1
                }
            }
            text[index] == '`' -> {
                val end = text.indexOf('`', startIndex = index + 1)
                if (end >= 0) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(text[index])
                    index += 1
                }
            }
            else -> {
                append(text[index])
                index += 1
            }
        }
    }
}
