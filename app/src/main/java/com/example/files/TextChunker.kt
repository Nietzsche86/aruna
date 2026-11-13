package com.example.files

data class TextChunk(
    val index: Int,
    val content: String,
)

object TextChunker {
    fun chunk(text: String, chunkSize: Int = 1400, overlap: Int = 150): List<TextChunk> {
        if (text.isBlank()) return emptyList()
        val normalized = text.trim()
        if (normalized.length <= chunkSize) {
            return listOf(TextChunk(index = 0, content = normalized))
        }
        val chunks = mutableListOf<TextChunk>()
        var start = 0
        var idx = 0
        val step = (chunkSize - overlap).coerceAtLeast(200)
        while (start < normalized.length) {
            val end = (start + chunkSize).coerceAtMost(normalized.length)
            chunks.add(TextChunk(index = idx++, content = normalized.substring(start, end)))
            if (end >= normalized.length) break
            start += step
        }
        return chunks
    }

    fun searchChunks(text: String, query: String, maxChunks: Int = 3): List<TextChunk> {
        val chunks = chunk(text)
        if (query.isBlank()) return chunks.take(maxChunks)
        val keywords = query.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
        if (keywords.isEmpty()) return chunks.take(maxChunks)

        return chunks
            .map { chunk ->
                val lower = chunk.content.lowercase()
                val score = keywords.sumOf { kw -> Regex(Regex.escape(kw)).findAll(lower).count() }
                chunk to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(maxChunks)
            .map { it.first }
            .ifEmpty { chunks.take(maxChunks) }
    }
}
