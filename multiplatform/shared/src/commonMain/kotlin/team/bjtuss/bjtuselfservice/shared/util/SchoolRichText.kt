package team.bjtuss.bjtuselfservice.shared.util

/**
 * 把学校侧可能是 HTML 或纯文本的「长说明」整理成适合 Compose Text 的多行纯文本。
 *
 * - HTML：`<br>` / 块级结束标签 → 换行，再去标签
 * - 纯文本：在「1. / 2、 / 1）」等条目序号前断行（老师常写成一整段）
 */
fun schoolRichTextToPlainMultiline(source: String?): String {
    if (source.isNullOrBlank()) return ""
    val trimmed = source.trim()
    val looksLikeHtml = trimmed.contains('<') && trimmed.contains('>')
    val base = if (looksLikeHtml) {
        htmlFragmentToMultiline(trimmed)
    } else {
        trimmed.replace("\r\n", "\n").replace('\r', '\n')
    }
    return breakNumberedItems(base)
        .lines()
        .map { it.replace(Regex("[ \\t\\u00a0]+"), " ").trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}

private fun htmlFragmentToMultiline(html: String): String {
    // 先在源串上把 br/块结束换成换行，避免 .text() 压成一行。
    val withBreaks = html
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p\\s*>"), "\n")
        .replace(Regex("(?i)</div\\s*>"), "\n")
        .replace(Regex("(?i)</li\\s*>"), "\n")
        .replace(Regex("(?i)</tr\\s*>"), "\n")
        .replace(Regex("(?i)</h[1-6]\\s*>"), "\n")
        .replace(Regex("(?i)<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
    return withBreaks.replace("\r\n", "\n").replace('\r', '\n')
}

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
