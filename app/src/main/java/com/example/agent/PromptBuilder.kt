package com.example.agent

import com.example.agent.model.AgentStoredMessage
import com.example.llm.LlmMessage
import com.example.memory.MemoryItem
import com.example.memory.UserProfile
import com.example.skills.SkillCatalogBuilder
import com.example.skills.SkillItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Constructs LLM prompts strictly ordered from most static (cacheable prefix)
 * to most dynamic (session-specific suffix) to maximize API Prompt Caching.
 */
class PromptBuilder(
    private val skillCatalogBuilder: SkillCatalogBuilder = SkillCatalogBuilder(),
) {
    fun buildPromptMessages(
        profile: UserProfile?,
        activeSkills: List<SkillItem>,
        relevantMemories: List<MemoryItem>,
        conversationSummary: String?,
        activeMessages: List<AgentStoredMessage>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<LlmMessage> {
        // 1. Static Prefix (cache-friendly): System instructions + Profile + Skill Headers
        val staticPrefix = buildString {
            appendLine(BASE_SYSTEM_PROMPT)
            val profileText = profile?.summaryText?.trim().orEmpty()
            if (profileText.isNotEmpty()) {
                appendLine()
                appendLine("## Profil Pengguna Ringkas")
                appendLine(profileText.take(MAX_PROFILE_CHARS))
            }
            val skillCatalog = skillCatalogBuilder.buildHeaderCatalog(activeSkills)
            if (skillCatalog.isNotEmpty()) {
                appendLine()
                appendLine(skillCatalog)
            }
        }.trim()

        // 2. Dynamic Context Block: Current Time + Relevant Memories + Session Summary
        val dateStr = SimpleDateFormat("EEEE, dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date(nowMillis))
        val dynamicContext = buildString {
            appendLine("Waktu perangkat saat ini: $dateStr")
            if (relevantMemories.isNotEmpty()) {
                appendLine()
                appendLine("## Memori Relevan tentang Pengguna")
                var charsUsed = 0
                for (mem in relevantMemories) {
                    val line = "- [${mem.category}] ${mem.text}"
                    if (charsUsed + line.length > MAX_MEMORY_CHARS) break
                    appendLine(line)
                    charsUsed += line.length
                }
            }
            if (!conversationSummary.isNullOrBlank()) {
                appendLine()
                appendLine("## Ringkasan Percakapan Sebelumnya")
                appendLine(conversationSummary.trim())
            }
        }.trim()

        val result = mutableListOf<LlmMessage>()
        result.add(LlmMessage(role = "system", content = staticPrefix))
        if (dynamicContext.isNotBlank()) {
            result.add(LlmMessage(role = "system", content = dynamicContext))
        }

        // 3. Uncompacted Active Messages + Attachment context
        activeMessages
            .filter { !it.isCompacted }
            .forEach { msg ->
                val combinedText = buildString {
                    append(msg.content)
                    if (!msg.documentContextNote.isNullOrBlank()) {
                        if (isNotEmpty()) appendLine().appendLine()
                        append(msg.documentContextNote)
                    }
                }
                result.add(
                    LlmMessage(
                        role = msg.role,
                        content = combinedText,
                        imageDataUrls = msg.imageDataUrls,
                        toolCalls = msg.toolCalls,
                        toolCallId = msg.toolCallId,
                        toolName = msg.toolName,
                    )
                )
            }

        return result
    }

    companion object {
        private const val MAX_PROFILE_CHARS = 1200 // ~300 tokens
        private const val MAX_MEMORY_CHARS = 3200  // ~800 tokens

        private val BASE_SYSTEM_PROMPT = """
            Anda adalah Aruna, asisten AI pribadi adaptif milik pengguna di perangkat Android.
            Jawablah dengan ringkas, akurat, ramah, dan gunakan bahasa pengguna (default bahasa Indonesia).
            Aturan penting:
            - Gunakan tool yang tersedia bila pengguna meminta membuat catatan, mencari riwayat chat, memasang alarm, membuat agenda kalender, menjadwalkan tugas otomatis, membaca isi skill, atau membaca bagian berkas lampiran.
            - Jika suatu permintaan cocok dengan katalog skill yang tersedia, panggil tool `baca_skill` terlebih dahulu untuk membaca langkah dan hal yang perlu dihindari sebelum bertindak.
            - Jangan mengarang hasil eksekusi tool; sampaikan hasil sebenarnya dengan jujur dan jelas.
        """.trimIndent()
    }
}
