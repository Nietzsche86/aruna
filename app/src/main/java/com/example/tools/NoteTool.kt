package com.example.tools

import com.example.agent.AgentTool
import com.example.agent.model.PermissionLevel
import com.example.agent.model.ToolExecutionContext
import com.example.agent.model.ToolExecutionResult
import com.example.data.FtsQueryHelper
import com.example.data.dao.NoteDao
import com.example.data.entity.NoteEntity
import java.util.UUID

class CreateNoteTool(
    private val noteDao: NoteDao,
) : AgentTool {
    override val name = "buat_catatan"
    override val description = "Membuat dan menyimpan catatan baru di penyimpanan lokal perangkat."
    override val permissionLevel = PermissionLevel.OTOMATIS
    override val parametersSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "title" to mapOf("type" to "string", "description" to "Judul catatan"),
            "body" to mapOf("type" to "string", "description" to "Isi lengkap catatan"),
        ),
        "required" to listOf("title", "body"),
    )

    override suspend fun execute(
        arguments: Map<String, Any?>,
        context: ToolExecutionContext,
    ): ToolExecutionResult {
        val title = arguments["title"]?.toString()?.trim().orEmpty()
        val body = arguments["body"]?.toString()?.trim().orEmpty()
        if (title.isBlank() && body.isBlank()) {
            return ToolExecutionResult(false, "Judul dan isi catatan tidak boleh kosong.")
        }
        val now = System.currentTimeMillis()
        val note = NoteEntity(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "Catatan Tanpa Judul" },
            body = body,
            createdAt = now,
            updatedAt = now,
        )
        noteDao.upsert(note)
        return ToolExecutionResult(true, "Catatan '${note.title}' berhasil disimpan (id=${note.id}).")
    }
}

class SearchNoteTool(
    private val noteDao: NoteDao,
) : AgentTool {
    override val name = "cari_catatan"
    override val description = "Mencari catatan lokal pengguna berdasarkan kata kunci."
    override val permissionLevel = PermissionLevel.BACA_SAJA
    override val parametersSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf("type" to "string", "description" to "Kata kunci pencarian catatan"),
        ),
        "required" to listOf("query"),
    )

    override suspend fun execute(
        arguments: Map<String, Any?>,
        context: ToolExecutionContext,
    ): ToolExecutionResult {
        val query = arguments["query"]?.toString()?.trim().orEmpty()
        val fts = FtsQueryHelper.toMatchQuery(query)
        val results = if (fts.isNotBlank()) {
            runCatching { noteDao.searchFts(fts, 10) }.getOrDefault(emptyList())
        } else {
            noteDao.getAll().take(10)
        }
        if (results.isEmpty()) {
            return ToolExecutionResult(true, "Tidak ditemukan catatan yang cocok dengan '$query'.")
        }
        val formatted = results.joinToString("\n\n") { n ->
            "• ${n.title}\n  ${n.body.take(400)}"
        }
        return ToolExecutionResult(true, "Ditemukan ${results.size} catatan:\n$formatted")
    }
}
