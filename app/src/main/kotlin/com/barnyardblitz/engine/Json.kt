package com.barnyardblitz.engine

/**
 * A minimal JSON reader/writer.
 *
 * The engine deliberately has no dependencies - not even kotlinx.serialization -
 * so the whole rules layer compiles and runs on a bare JVM. Values map to
 * `Map<String, Any?>`, `List<Any?>`, `String`, `Double`, `Boolean` and `null`.
 */
object Json {

    fun encode(value: Any?): String = StringBuilder().also { write(value, it) }.toString()

    private fun write(value: Any?, out: StringBuilder) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(if (value) "true" else "false")
            is Int -> out.append(value.toString())
            is Long -> out.append(value.toString())
            is Double -> out.append(if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString())
            is Float -> write(value.toDouble(), out)
            is String -> writeString(value, out)
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) out.append(',')
                    first = false
                    writeString(k.toString(), out)
                    out.append(':')
                    write(v, out)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (item in value) {
                    if (!first) out.append(',')
                    first = false
                    write(item, out)
                }
                out.append(']')
            }
            else -> writeString(value.toString(), out)
        }
    }

    private fun writeString(text: String, out: StringBuilder) {
        out.append('"')
        for (ch in text) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (ch < ' ') out.append("\\u%04x".format(ch.code)) else out.append(ch)
            }
        }
        out.append('"')
    }

    /** Throws [JsonException] on malformed input. */
    fun decode(text: String): Any? = Parser(text).run {
        val value = parseValue()
        skipWhitespace()
        if (!atEnd()) fail("trailing content")
        value
    }

    class JsonException(message: String) : RuntimeException(message)

    private class Parser(private val src: String) {
        private var pos = 0

        fun atEnd() = pos >= src.length

        fun fail(why: String): Nothing = throw JsonException("$why at index $pos")

        fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        fun parseValue(): Any? {
            skipWhitespace()
            if (atEnd()) fail("unexpected end")
            return when (val ch = src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (ch == '-' || ch.isDigit()) parseNumber() else fail("unexpected '$ch'")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!src.startsWith(word, pos)) fail("bad literal")
            pos += word.length
            return value
        }

        private fun parseObject(): Map<String, Any?> {
            pos++ // {
            val out = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (!atEnd() && src[pos] == '}') { pos++; return out }
            while (true) {
                skipWhitespace()
                if (atEnd() || src[pos] != '"') fail("expected key")
                val key = parseString()
                skipWhitespace()
                if (atEnd() || src[pos] != ':') fail("expected ':'")
                pos++
                out[key] = parseValue()
                skipWhitespace()
                if (atEnd()) fail("unterminated object")
                when (src[pos]) {
                    ',' -> pos++
                    '}' -> { pos++; return out }
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            pos++ // [
            val out = ArrayList<Any?>()
            skipWhitespace()
            if (!atEnd() && src[pos] == ']') { pos++; return out }
            while (true) {
                out.add(parseValue())
                skipWhitespace()
                if (atEnd()) fail("unterminated array")
                when (src[pos]) {
                    ',' -> pos++
                    ']' -> { pos++; return out }
                    else -> fail("expected ',' or ']'")
                }
            }
        }

        private fun parseString(): String {
            pos++ // opening quote
            val out = StringBuilder()
            while (true) {
                if (atEnd()) fail("unterminated string")
                when (val ch = src[pos++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (atEnd()) fail("dangling escape")
                        when (val esc = src[pos++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (pos + 4 > src.length) fail("short unicode escape")
                                out.append(src.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> fail("bad escape '$esc'")
                        }
                    }
                    else -> out.append(ch)
                }
            }
        }

        private fun parseNumber(): Double {
            val start = pos
            if (!atEnd() && src[pos] == '-') pos++
            while (!atEnd() && (src[pos].isDigit() || src[pos] in ".eE+-")) pos++
            return src.substring(start, pos).toDoubleOrNull() ?: fail("bad number")
        }
    }
}

// ---------------------------------------------------------------- accessors

@Suppress("UNCHECKED_CAST")
fun Any?.asMap(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

fun Any?.asList(): List<Any?> = this as? List<Any?> ?: emptyList()

fun Map<String, Any?>.int(key: String, fallback: Int = 0): Int =
    when (val v = this[key]) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: fallback
        else -> fallback
    }

fun Map<String, Any?>.double(key: String, fallback: Double = 0.0): Double =
    when (val v = this[key]) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull() ?: fallback
        else -> fallback
    }

fun Map<String, Any?>.str(key: String, fallback: String = ""): String =
    this[key] as? String ?: fallback

fun Map<String, Any?>.strings(key: String): List<String> =
    this[key].asList().mapNotNull { it as? String }
