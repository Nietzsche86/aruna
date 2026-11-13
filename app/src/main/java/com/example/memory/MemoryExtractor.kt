package com.example.memory

import com.example.llm.LlmConfig
import com.example.llm.LlmMessage
import com.example.llm.LlmProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID

class MemoryExtractor(
    private val llmProvider: LlmProvider,
    private val memoryRepository: MemoryRepository,
    moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
) {
    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    private val mapAdapter = moshi.adapter<Map<String, Any>>(mapType)

    suspend fun extractAndApply(
        utilityConfig: LlmConfig,
        recentMessages: List<LlmMessage>,
        sourceMessageId: String? = null,
    ): Int {
        if (recentMessages.isEmpty()) return 0
        val existingMemories = memoryRepository.getAllMemories()
        val existingText = if (existingMemories.isEmpty()) {
            "(Belum ada memori tersimpan)"
        } else {
            existingMemories.take(60).joinToString("\n") { mem ->
                "- id=${mem.id} | category=${mem.category} | text=${mem.text}"
            }
        }

        val transcript = recentMessages
            .filter { it.role == "user" || it.role == "assistant" }
            .takeLast(16)
            .joinToString("\n") { "${it.role.uppercase()}: ${it.content.take(800)}" }

        if (transcript.isBlank()) return 0

        val systemPrompt = """
            Anda adalah mesin ekstraksi memori jangka panjang untuk asisten pribadi.
            Tugas Anda mengekstrak fakta permanen/penting tentang pengguna (preferensi, pekerjaan, kebiasaan, tujuan, jadwal rutin, relasi, kesehatan, keuangan) dari transkrip percakapan.
            Bandingkan dengan MEMORI YANG ADA untuk menghindari duplikasi dan menyelesaikan konflik.
            Kembalikan HANYA JSON valid dengan format:
            {
              "operations": [
                {"action": "ADD", "category": "preferensi|pekerjaan|rutinitas|tujuan|kesehatan|keuangan|kredensial|lainnya", "text": "fakta ringkas", "tags": "tag1,tag2", "sensitive": false},
                {"action": "UPDATE", "id": "id_memori_lama", "category": "...", "text": "fakta terbaru", "tags": "...", "sensitive": false},
                {"action": "DELETE", "id": "id_memori_usang"}
              ]
            }
            Aturan ketat:
            1. Gunakan UPDATE jika fakta lama berubah atau dikoreksi pengguna.
            2. Gunakan DELETE jika fakta lama sudah tidak berlaku.
            3. Tandai "sensitive": true jika menyangkut kesehatan, keuangan, atau kredensial/kata sandi.
            4. Jika tidak ada fakta baru yang layak disimpan, kembalikan {"operations": []}.
        """.trimIndent()

        val userPrompt = """
            MEMORI YANG ADA:
            $existingText

            TRANSKRIP TERBARU:
            $transcript
        """.trimIndent()

        val response = runCatching {
            llmProvider.chat(
                config = utilityConfig,
                messages = listOf(
                    LlmMessage(role = "system", content = systemPrompt),
                    LlmMessage(role = "user", content = userPrompt),
                ),
            )
        }.getOrNull() ?: return 0

        val ops = parseOperations(response.content)
        var appliedCount = 0
        val existingById = existingMemories.associateBy { it.id }

        for (op in ops) {
            when (op.action) {
                MemoryActionType.ADD -> {
                    if (op.text.isNotBlank()) {
                        val isSensitive = op.sensitive || isSensitiveCategory(op.category)
                        memoryRepository.addMemory(
                            MemoryItem(
                                id = UUID.randomUUID().toString(),
                                text = op.text.trim(),
                                category = op.category.ifBlank { "lainnya" },
                                tags = op.tags,
                                sourceMessageId = sourceMessageId,
                                enabled = !isSensitive,
                                pendingConfirmation = isSensitive,
                            )
                        )
                        appliedCount++
                    }
                }
                MemoryActionType.UPDATE -> {
                    val targetId = op.id
                    if (!targetId.isNullOrBlank() && existingById.containsKey(targetId) && op.text.isNotBlank()) {
                        memoryRepository.updateMemory(
                            id = targetId,
                            newText = op.text.trim(),
                            category = op.category.ifBlank { existingById[targetId]?.category ?: "lainnya" },
                            tags = op.tags,
                        )
                        appliedCount++
                    }
                }
                MemoryActionType.DELETE -> {
                    val targetId = op.id
                    if (!targetId.isNullOrBlank() && existingById.containsKey(targetId)) {
                        memoryRepository.deleteMemory(targetId)
                        appliedCount++
                    }
                }
            }
        }
        return appliedCount
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseOperations(rawContent: String): List<MemoryOperation> {
        val cleaned = extractJsonObject(rawContent)
        val json = runCatching { mapAdapter.fromJson(cleaned) }.getOrNull() ?: return emptyList()
        val opsRaw = json["operations"] as? List<Map<String, Any?>> ?: return emptyList()
        return opsRaw.mapNotNull { item ->
            val actionStr = item["action"]?.toString()?.uppercase()?.trim() ?: return@mapNotNull null
            val action = runCatching { MemoryActionType.valueOf(actionStr) }.getOrNull() ?: return@mapNotNull null
            val category = item["category"]?.toString()?.lowercase()?.trim().orEmpty()
            val sensitiveFlag = (item["sensitive"] as? Boolean) == true || isSensitiveCategory(category)
            MemoryOperation(
                action = action,
                id = item["id"]?.toString()?.trim(),
                text = item["text"]?.toString()?.trim().orEmpty(),
                category = category.ifBlank { "lainnya" },
                tags = item["tags"]?.toString()?.trim().orEmpty(),
                sensitive = sensitiveFlag,
            )
        }
    }

    internal fun isSensitiveCategory(category: String): Boolean {
        val normalized = category.lowercase()
        return normalized.contains("kesehatan") ||
            normalized.contains("health") ||
            normalized.contains("keuangan") ||
            normalized.contains("finance") ||
            normalized.contains("kredensial") ||
            normalized.contains("credential")
    }

    private fun extractJsonObject(raw: String): String {
        val trimmed = raw.trim()
        val fenced = Regex("```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```").find(trimmed)?.groupValues?.get(1)
        if (!fenced.isNullOrBlank()) return fenced
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else "{}"
    }
}
