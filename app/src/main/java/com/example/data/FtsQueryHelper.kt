package com.example.data

object FtsQueryHelper {
    fun toMatchQuery(raw: String): String {
        val tokens = raw
            .trim()
            .split(Regex("\\s+"))
            .map { token -> token.replace(Regex("[^\\p{L}\\p{N}_]"), "") }
            .filter { it.length >= 2 }
            .distinct()
            .take(8)
        if (tokens.isEmpty()) return ""
        return tokens.joinToString(" OR ") { "$it*" }
    }
}
