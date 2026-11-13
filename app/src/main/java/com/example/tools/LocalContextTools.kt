package com.example.tools

import com.example.agent.AgentTool
import com.example.agent.model.PermissionLevel
import com.example.agent.model.ToolExecutionContext
import com.example.agent.model.ToolExecutionResult
import com.example.data.dao.AttachmentDao
import com.example.data.repository.ChatRepositoryImpl
import com.example.files.TextChunker
import com.example.skills.SkillRepository

class ChatSearchTool(
    private val chatRepository: ChatRepositoryImpl,
) : AgentTool {
    override val name = "cari_riwayat_chat"
    override val description = "Mencari pesan di riwayat percakapan terdahulu menggunakan pencarian teks penuh."
    override val permissionLevel = PermissionLevel.BACA_SAJA
    override val parametersSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf("type" to "string", "description" to "Kata kunci yang ingin dicari di riwayat obrolan"),
        ),
        "required" to listOf("query"),
    )

    override suspend fun execute(
        arguments: Map<String, Any?>,
        context: ToolExecutionContext,
    ): ToolExecutionResult {
        val query = arguments["query"]?.toString()?.trim().orEmpty()
        if (query.isBlank()) return ToolExecutionResult(false, "Kata kunci pencarian wajib diisi.")
        val matches = chatRepository.searchMessagesFts(query, limit = 10)
        if (matches.isEmpty()) {
            return ToolExecutionResult(true, "Tidak ditemukan riwayat percakapan yang cocok dengan '$query'.")
        }
        val formatted = matches.joinToString("\n") { msg ->
            "- [${msg.role}] ${msg.content.replace('\n', ' ').take(240)}"
        }
        return ToolExecutionResult(true, "Hasil pencarian riwayat ($query):\n$formatted")
    }
}

class SkillReadTool(
    private val skillRepository: SkillRepository,
) : AgentTool {
    override val name = "baca_skill"
    override val description = "Memuat langkah lengkap, hal yang harus dihindari (pitfalls), dan cara verifikasi dari sebuah skill berdasarkan id."
    override val permissionLevel = PermissionLevel.BACA_SAJA
    override val parametersSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "id" to mapOf("type" to "string", "description" to "ID skill dari katalog sistem"),
        ),
        "required" to listOf("id"),
    )

    override suspend fun execute(
        arguments: Map<String, Any?>,
        context: ToolExecutionContext,
    ): ToolExecutionResult {
        val id = arguments["id"]?.toString()?.trim().orEmpty()
        val skill = skillRepository.getSkillById(id)
            ?: return ToolExecutionResult(false, "Skill dengan id='$id' tidak ditemukan.")
        skillRepository.incrementUseCount(id)
        val details = buildString {
            appendLine("# Skill: ${skill.name}")
            appendLine("Kapan dipakai: ${skill.whenToUse}")
            appendLine()
            appendLine("Langkah-langkah:")
            appendLine(skill.steps)
            if (skill.pitfalls.isNotBlank()) {
                appendLine()
                appendLine("Hal yang harus dihindari:")
                appendLine(skill.pitfalls)
            }
            if (skill.verification.isNotBlank()) {
                appendLine()
                appendLine("Verifikasi:")
                appendLine(skill.verification)
            }
        }.trim()
        return ToolExecutionResult(true, details)
    }
}

class FileReadTool(
    private val attachmentDao: AttachmentDao,
) : AgentTool {
    override val name = "baca_berkas"
    override val description = "Membaca atau mencari bagian (chunk) tertentu dari berkas dokumen lampiran berukuran besar."
    override val permissionLevel = PermissionLevel.BACA_SAJA
    override val parametersSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "attachment_id" to mapOf("type" to "string", "description" to "ID lampiran berkas"),
            "query" to mapOf("type" to "string", "description" to "Kata kunci untuk mencari bagian paling relevan (opsional)"),
            "chunk_index" to mapOf("type" to "integer", "description" to "Indeks bagian ke-N (dimulai dari 0) jika membaca berurutan"),
        ),
        "required" to listOf("attachment_id"),
    )

    override suspend fun execute(
        arguments: Map<String, Any?>,
        context: ToolExecutionContext,
    ): ToolExecutionResult {
        val attId = arguments["attachment_id"]?.toString()?.trim().orEmpty()
        val att = attachmentDao.getById(attId)
            ?: return ToolExecutionResult(false, "Lampiran dengan id='$attId' tidak ditemukan.")
        val fullText = att.extractedText.orEmpty()
        if (fullText.isBlank()) {
            return ToolExecutionResult(false, "Berkas '${att.fileName}' tidak memiliki teks terekstrak.")
        }

        val query = arguments["query"]?.toString()?.trim().orEmpty()
        val chunkIndex = (arguments["chunk_index"] as? Number)?.toInt()

        val allChunks = TextChunker.chunk(fullText)
        val selected = when {
            chunkIndex != null -> listOfNotNull(allChunks.getOrNull(chunkIndex))
            query.isNotBlank() -> TextChunker.searchChunks(fullText, query, maxChunks = 3)
            else -> allChunks.take(2)
        }

        if (selected.isEmpty()) {
            return ToolExecutionResult(false, "Indeks chunk di luar rentang (total ${allChunks.size} bagian).")
        }

        val body = selected.joinToString("\n\n---\n\n") { chunk ->
            "[Bagian ${chunk.index + 1}/${allChunks.size} dari '${att.fileName}']\n${chunk.content}"
        }
        return ToolExecutionResult(true, body)
    }
}
