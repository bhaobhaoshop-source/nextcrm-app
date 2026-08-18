package com.estatedesk.crm.core

/** Minimal, correct RFC-4180 CSV reader/writer (quotes, escaped quotes,
 *  embedded commas/newlines). Pure Kotlin — no dependencies. */
object Csv {

    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val n = text.length
        while (i < n) {
            val ch = text[i]
            when {
                inQuotes -> {
                    when {
                        ch == '"' && i + 1 < n && text[i + 1] == '"' -> {
                            field.append('"'); i++
                        }
                        ch == '"' -> inQuotes = false
                        else -> field.append(ch)
                    }
                }
                ch == '"' -> inQuotes = true
                ch == ',' -> {
                    row.add(field.toString()); field.setLength(0)
                }
                ch == '\r' && i + 1 < n && text[i + 1] == '\n' -> {
                    row.add(field.toString()); field.setLength(0)
                    rows.add(row); row = mutableListOf(); i++
                }
                ch == '\n' -> {
                    row.add(field.toString()); field.setLength(0)
                    rows.add(row); row = mutableListOf()
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        // drop fully-empty trailing rows
        while (rows.isNotEmpty() && rows.last().all { it.isBlank() } && rows.last().size <= 1) {
            rows.removeAt(rows.size - 1)
        }
        return rows
    }

    fun escape(value: String): String {
        if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }

    fun write(header: List<String>, rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append(header.joinToString(",") { escape(it) }).append("\r\n")
        for (r in rows) {
            sb.append(r.joinToString(",") { escape(it) }).append("\r\n")
        }
        return sb.toString()
    }
}
