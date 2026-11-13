package com.example.memory

import com.example.llm.LlmConfig
import com.example.llm.LlmMessage
import com.example.llm.LlmProvider

class ConversationSummarizer(
    private val llmProvider: LlmProvider,
) {
    suspend fun summarize(
        utilityConfig: LlmConfig,
        existingSummary: String?,
        messagesToCompact: List<LlmMessage>,
    ): String {
        if (messagesToCompact.isEmpty()) return existingSummary.orEmpty()

        val transcript = messagesToCompact.joinToString("\n") { msg ->
            "[${msg.role.uppercase()}]: ${msg.content.take(1200)}"
        }

        val prompt = buildString {
            if (!existingSummary.isNullOrBlank()) {
                appendLine("RINGKASAN SEBELUMNYA:")
                appendLine(existingSummary)
                appendLine()
            }
            appendLine("PESAN LAMA YANG AKAN DIRANGKUM:")
            appendLine(transcript)
        }

        val response = runCatching {
            llmProvider.chat(
                config = utilityConfig,
                messages = listOf(
                    LlmMessage(
                        role = "system",
                        content = "Rangkum percakapan berikut secara padat dan terstruktur dalam bahasa Indonesia (maksimal 250 kata). Pertahankan keputusan penting, konteks tugas, nama file/entitas, dan kesepakatan pengguna.",
                    ),
                    LlmMessage(role = "user", content = prompt),
                ),
            )
        }.getOrNull()

        return response?.content?.trim()?.takeIf { it.isNotBlank() }
            ?: buildFallbackSummary(existingSummary, messagesToCompact)
    }

    private fun buildFallbackSummary(
        existingSummary: String?,
        messages: List<LlmMessage>,
    ): String {
        val snippets = messages
            .filter { it.role == "user" || it.role == "assistant" }
            .takeLast(6)
            .joinToString(" | ") { "${it.role}: ${it.content.replace('\n', ' ').take(100)}" }
        return listOfNotNull(existingSummary?.takeIf { it.isNotBlank() }, snippets).joinToString("\n")
    }
}

class ProfileUpdater(
    private val llmProvider: LlmProvider,
    private val memoryRepository: MemoryRepository,
) {
    suspend fun refreshProfile(utilityConfig: LlmConfig): String? {
        val memories = memoryRepository.getActiveConfirmedMemories()
        if (memories.isEmpty()) return null

        val existing = memoryRepository.getProfile()?.summaryText.orEmpty()
        val memLines = memories.take(50).joinToString("\n") { "- [${it.category}] ${it.text}" }

        val response = runCatching {
            llmProvider.chat(
                config = utilityConfig,
                messages = listOf(
                    LlmMessage(
                        role = "system",
                        content = "Susun profil ringkas pengguna (maksimal 220 kata / ~300 token) dari fakta memori aktif berikut. Gunakan poin singkat yang jelas, padat, dan netral.",
                    ),
                    LlmMessage(
                        role = "user",
                        content = "PROFIL SAAT INI:\n$existing\n\nMEMORI AKTIF:\n$memLines",
                    ),
                ),
            )
        }.getOrNull()?.content?.trim() ?: return null

        if (response.isNotBlank()) {
            memoryRepository.saveProfile(response)
        }
        return response
    }
}
