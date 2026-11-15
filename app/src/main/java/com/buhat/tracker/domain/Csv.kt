package com.buhat.tracker.domain

/** One CSV record. [line] is the 1-based physical line where the record starts. */
data class CsvRow(val line: Int, val fields: List<String>)

class CsvException(message: String) : Exception(message)

// RFC 4180-style reader. Strong exports use ';' and quote every text field.
fun parseCsv(text: String, delimiter: Char = ';'): List<CsvRow> {
    val src = text.removePrefix("\uFEFF")
    val rows = mutableListOf<CsvRow>()
    var i = 0
    var line = 1

    while (i < src.length) {
        val c = src[i]
        if (c == '\r' || c == '\n') {
            i += if (c == '\r' && i + 1 < src.length && src[i + 1] == '\n') 2 else 1
            line++
            continue
        }

        val startLine = line
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var fieldStart = true
        var inQuotes = false

        while (true) {
            if (i >= src.length) {
                if (inQuotes) throw CsvException("Row $startLine: The quoted field is never closed.")
                fields.add(field.toString())
                break
            }
            val ch = src[i]
            if (inQuotes) {
                when {
                    ch == '"' && i + 1 < src.length && src[i + 1] == '"' -> {
                        field.append('"')
                        i += 2
                    }
                    ch == '"' -> {
                        i++
                        val next = if (i < src.length) src[i] else null
                        if (next != null && next != delimiter && next != '\r' && next != '\n') {
                            throw CsvException("Row $startLine: Unexpected text after a closing quote.")
                        }
                        inQuotes = false
                    }
                    ch == '\r' || ch == '\n' -> {
                        field.append('\n')
                        i += if (ch == '\r' && i + 1 < src.length && src[i + 1] == '\n') 2 else 1
                        line++
                    }
                    else -> {
                        field.append(ch)
                        i++
                    }
                }
            } else {
                when (ch) {
                    delimiter -> {
                        fields.add(field.toString())
                        field.clear()
                        i++
                        fieldStart = true
                    }
                    '"' -> {
                        if (!fieldStart) {
                            throw CsvException("Row $startLine: Unexpected quote inside an unquoted field.")
                        }
                        inQuotes = true
                        fieldStart = false
                        i++
                    }
                    '\r', '\n' -> {
                        fields.add(field.toString())
                        i += if (ch == '\r' && i + 1 < src.length && src[i + 1] == '\n') 2 else 1
                        line++
                        break
                    }
                    else -> {
                        field.append(ch)
                        fieldStart = false
                        i++
                    }
                }
            }
        }
        rows.add(CsvRow(startLine, fields))
    }
    return rows
}
