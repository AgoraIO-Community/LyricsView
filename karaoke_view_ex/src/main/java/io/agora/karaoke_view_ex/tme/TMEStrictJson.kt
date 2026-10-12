package io.agora.karaoke_view_ex.tme

/**
 * Gson 2.10's non-lenient reader still accepts case-insensitive literals and raw
 * control characters in strings. Check JSON token syntax before its structure parser.
 */
internal object TMEStrictJson {
    private val number = Regex("""-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?""")

    fun validate(raw: String) {
        var cursor = 0
        while (cursor < raw.length) {
            when (val char = raw[cursor]) {
                ' ', '\t', '\r', '\n', '{', '}', '[', ']', ':', ',' -> cursor++
                '"' -> cursor = stringEnd(raw, cursor + 1)
                't', 'f', 'n' -> {
                    val literal = when (char) { 't' -> "true"; 'f' -> "false"; else -> "null" }
                    require(raw.startsWith(literal, cursor))
                    cursor += literal.length
                    requireBoundary(raw, cursor)
                }
                '-', in '0'..'9' -> {
                    val token = number.find(raw, cursor)
                    require(token != null && token.range.first == cursor)
                    cursor = token.range.last + 1
                    requireBoundary(raw, cursor)
                }
                else -> throw IllegalArgumentException("Invalid JSON token")
            }
        }
    }

    private fun stringEnd(raw: String, start: Int): Int {
        var cursor = start
        while (cursor < raw.length) {
            val char = raw[cursor++]
            require(char >= ' ')
            if (char == '"') return cursor
            if (char != '\\') continue
            require(cursor < raw.length)
            when (raw[cursor++]) {
                '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> Unit
                'u' -> {
                    repeat(4) {
                        require(cursor < raw.length)
                        val hex = raw[cursor++]
                        require(hex in '0'..'9' || hex in 'a'..'f' || hex in 'A'..'F')
                    }
                }
                else -> throw IllegalArgumentException("Invalid JSON escape")
            }
        }
        throw IllegalArgumentException("Unterminated JSON string")
    }

    private fun requireBoundary(raw: String, cursor: Int) {
        require(cursor == raw.length || raw[cursor] in " \t\r\n{}[]:,")
    }
}
