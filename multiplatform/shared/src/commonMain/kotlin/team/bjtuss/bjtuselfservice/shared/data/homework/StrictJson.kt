package team.bjtuss.bjtuselfservice.shared.data.homework

/**
 * 智慧教学平台没有稳定的 schema。这里仅解析标准 JSON 值，不把响应正文或游标附近内容
 * 放入异常，避免登录后页面数据进入日志。调用者负责把缺字段转换为枚举错误。
 */
internal sealed interface StrictJsonValue {
    data class ObjectValue(val fields: Map<String, StrictJsonValue>) : StrictJsonValue
    data class ArrayValue(val items: List<StrictJsonValue>) : StrictJsonValue
    data class StringValue(val value: String) : StrictJsonValue
    data class NumberValue(val raw: String) : StrictJsonValue
    data class BooleanValue(val value: Boolean) : StrictJsonValue
    data object NullValue : StrictJsonValue
}

internal fun parseStrictJsonObject(text: String): Map<String, StrictJsonValue>? = try {
    val parser = StrictJsonParser(text)
    val value = parser.parse()
    (value as? StrictJsonValue.ObjectValue)?.fields
} catch (_: IllegalArgumentException) {
    null
}

/** 解析 JSON 数组根值（如校历页 hidJson）；根不是数组或解析失败返回 null。 */
internal fun parseStrictJsonArray(text: String): List<StrictJsonValue>? = try {
    val parser = StrictJsonParser(text)
    val value = parser.parse()
    (value as? StrictJsonValue.ArrayValue)?.items
} catch (_: IllegalArgumentException) {
    null
}

internal fun Map<String, StrictJsonValue>.hasSuccessStatus(): Boolean = when (val status = string("STATUS")) {
    null, "", "0" -> true
    else -> false
}

/**
 * 智慧平台用 STATUS="2"（message 常为“没有数据”）表示“该课程当前类型没有作业”，
 * 这是合法的空列表而非错误。原 Android 以默认值容忍任意 STATUS 直接得到空列表；
 * KMP 严格解析若把 "2" 当失败，会在第一门无作业课程处中断整批同步。
 */
internal fun Map<String, StrictJsonValue>.isEmptyDataStatus(): Boolean =
    string("STATUS") == "2" && arrayOrBlank("courseNoteList") == null

internal fun Map<String, StrictJsonValue>.string(name: String): String? = when (val value = get(name)) {
    is StrictJsonValue.StringValue -> value.value
    is StrictJsonValue.NumberValue -> value.raw
    is StrictJsonValue.BooleanValue -> value.value.toString()
    else -> null
}

internal fun Map<String, StrictJsonValue>.int(name: String): Int? = string(name)?.toIntOrNull()

internal fun Map<String, StrictJsonValue>.long(name: String): Long? = string(name)?.toLongOrNull()

internal fun Map<String, StrictJsonValue>.boolean(name: String): Boolean? = when (val value = get(name)) {
    is StrictJsonValue.BooleanValue -> value.value
    is StrictJsonValue.StringValue -> value.value.toBooleanStrictOrNull()
    else -> null
}

internal fun Map<String, StrictJsonValue>.array(name: String): List<StrictJsonValue>? =
    (get(name) as? StrictJsonValue.ArrayValue)?.items

/** 智慧教学平台在空结果时既可能返回 []，也可能返回空字符串。 */
internal fun Map<String, StrictJsonValue>.arrayOrBlank(name: String): List<StrictJsonValue>? = when (val value = get(name)) {
    is StrictJsonValue.ArrayValue -> value.items
    is StrictJsonValue.StringValue -> if (value.value.isBlank()) emptyList() else null
    else -> null
}

internal fun StrictJsonValue?.asObject(): Map<String, StrictJsonValue>? =
    (this as? StrictJsonValue.ObjectValue)?.fields

private class StrictJsonParser(private val source: String) {
    private var index = 0

    fun parse(): StrictJsonValue {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        require(index == source.length)
        return value
    }

    private fun parseValue(): StrictJsonValue {
        skipWhitespace()
        require(index < source.length)
        return when (source[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> StrictJsonValue.StringValue(parseString())
            't' -> parseLiteral("true", StrictJsonValue.BooleanValue(true))
            'f' -> parseLiteral("false", StrictJsonValue.BooleanValue(false))
            'n' -> parseLiteral("null", StrictJsonValue.NullValue)
            '-', in '0'..'9' -> parseNumber()
            else -> throw IllegalArgumentException("invalid json")
        }
    }

    private fun parseObject(): StrictJsonValue.ObjectValue {
        expect('{')
        skipWhitespace()
        if (consume('}')) return StrictJsonValue.ObjectValue(emptyMap())
        val fields = linkedMapOf<String, StrictJsonValue>()
        while (true) {
            skipWhitespace()
            require(peek() == '"')
            val key = parseString()
            skipWhitespace()
            expect(':')
            fields[key] = parseValue()
            skipWhitespace()
            if (consume('}')) break
            expect(',')
        }
        return StrictJsonValue.ObjectValue(fields)
    }

    private fun parseArray(): StrictJsonValue.ArrayValue {
        expect('[')
        skipWhitespace()
        if (consume(']')) return StrictJsonValue.ArrayValue(emptyList())
        val items = mutableListOf<StrictJsonValue>()
        while (true) {
            items += parseValue()
            skipWhitespace()
            if (consume(']')) break
            expect(',')
        }
        return StrictJsonValue.ArrayValue(items)
    }

    private fun parseString(): String {
        expect('"')
        val value = StringBuilder()
        while (index < source.length) {
            val character = source[index++]
            when {
                character == '"' -> return value.toString()
                character == '\\' -> value.append(parseEscape())
                character.code < 0x20 -> throw IllegalArgumentException("invalid json")
                else -> value.append(character)
            }
        }
        throw IllegalArgumentException("invalid json")
    }

    private fun parseEscape(): Char {
        require(index < source.length)
        return when (val escaped = source[index++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000c'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> parseUnicodeEscape()
            else -> throw IllegalArgumentException("invalid json")
        }
    }

    private fun parseUnicodeEscape(): Char {
        require(index + 4 <= source.length)
        var value = 0
        repeat(4) {
            value = value * 16 + source[index++].hexValue()
        }
        return value.toChar()
    }

    private fun parseNumber(): StrictJsonValue.NumberValue {
        val start = index
        consume('-')
        if (consume('0')) {
            require(peek()?.isDigit() != true)
        } else {
            require(consumeDigits())
        }
        if (consume('.')) require(consumeDigits())
        if (peek() == 'e' || peek() == 'E') {
            index++
            if (peek() == '+' || peek() == '-') index++
            require(consumeDigits())
        }
        return StrictJsonValue.NumberValue(source.substring(start, index))
    }

    private fun <T : StrictJsonValue> parseLiteral(
        literal: String,
        value: T,
    ): T {
        require(source.regionMatches(index, literal, 0, literal.length))
        index += literal.length
        return value
    }

    private fun consumeDigits(): Boolean {
        val start = index
        while (peek()?.isDigit() == true) index++
        return index > start
    }

    private fun skipWhitespace() {
        while (peek() == ' ' || peek() == '\n' || peek() == '\r' || peek() == '\t') index++
    }

    private fun expect(character: Char) {
        require(consume(character))
    }

    private fun consume(character: Char): Boolean {
        if (peek() != character) return false
        index++
        return true
    }

    private fun peek(): Char? = source.getOrNull(index)
}

private fun Char.hexValue(): Int = when (this) {
    in '0'..'9' -> code - '0'.code
    in 'a'..'f' -> code - 'a'.code + 10
    in 'A'..'F' -> code - 'A'.code + 10
    else -> throw IllegalArgumentException("invalid json")
}
