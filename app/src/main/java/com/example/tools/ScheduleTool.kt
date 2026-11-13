package com.example.tools

import com.example.agent.AgentTool
import com.example.agent.model.PermissionLevel
import com.example.agent.model.ToolExecutionContext
import com.example.agent.model.ToolExecutionResult
import com.example.data.dao.ScheduledTaskDao
import com.example.data.entity.ScheduledTaskEntity
import com.example.scheduler.CronParser
import com.example.scheduler.TaskScheduler
import java.util.UUID

class ScheduleTool(
    private val taskDao: ScheduledTaskDao,
    private val taskScheduler: TaskScheduler,
) : AgentTool {
    override val name = "kelola_tugas_terjadwal"
    override val description = "Membuat, melihat, mengaktifkan, atau menonaktifkan tugas terjadwal (format cron 5 bagian: menit jam tanggal bulan hari)."
    override val permissionLevel = PermissionLevel.TANYA_DULU
    override val parametersSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "action" to mapOf("type" to "string", "description" to "Salah satu: buat, lihat, nonaktifkan, aktifkan"),
            "title" to mapOf("type" to "string", "description" to "Nama singkat tugas (wajib jika action=buat)"),
            "prompt" to mapOf("type" to "string", "description" to "Instruksi prompt yang akan dijalankan agent (wajib jika action=buat)"),
            "cron" to mapOf("type" to "string", "description" to "Ekspresi cron 5 bagian, misal '0 7 * * *' untuk setiap jam 07:00"),
            "task_id" to mapOf("type" to "string", "description" to "ID tugas untuk nonaktifkan/aktifkan"),
        ),
        "required" to listOf("action"),
    )

    override fun summarizeCall(arguments: Map<String, Any?>): String {
        val action = arguments["action"]?.toString() ?: "lihat"
        val title = arguments["title"]?.toString().orEmpty()
        val cron = arguments["cron"]?.toString().orEmpty()
        return "Jadwal ($action): $title $cron".trim()
    }

    override suspend fun execute(
        arguments: Map<String, Any?>,
        context: ToolExecutionContext,
    ): ToolExecutionResult {
        val action = arguments["action"]?.toString()?.lowercase()?.trim().orEmpty()
        return when (action) {
            "lihat", "list" -> {
                val tasks = taskDao.getAll()
                if (tasks.isEmpty()) {
                    ToolExecutionResult(true, "Belum ada tugas terjadwal.")
                } else {
                    val lines = tasks.joinToString("\n") { t ->
                        val status = if (t.enabled) "AKTIF" else "NONAKTIF"
                        "- [${t.id}] ${t.title} ($status, ${CronParser.describe(t.cron)}) -> ${t.prompt.take(80)}"
                    }
                    ToolExecutionResult(true, "Daftar tugas terjadwal:\n$lines")
                }
            }
            "buat", "create" -> {
                val title = arguments["title"]?.toString()?.trim().orEmpty().ifBlank { "Tugas Terjadwal" }
                val prompt = arguments["prompt"]?.toString()?.trim().orEmpty()
                val cron = arguments["cron"]?.toString()?.trim().orEmpty()
                if (prompt.isBlank() || cron.isBlank()) {
                    return ToolExecutionResult(false, "Parameter 'prompt' dan 'cron' wajib diisi untuk membuat jadwal.")
                }
                if (!CronParser.isValid(cron)) {
                    return ToolExecutionResult(false, "Ekspresi cron tidak valid: '$cron'. Gunakan 5 bagian (misal '0 7 * * *').")
                }
                val task = ScheduledTaskEntity(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    prompt = prompt,
                    cron = cron,
                    enabled = true,
                )
                taskDao.upsert(task)
                taskScheduler.scheduleTask(task)
                ToolExecutionResult(
                    true,
                    "Tugas '${task.title}' dijadwalkan (${CronParser.describe(cron)}, id=${task.id}).",
                )
            }
            "nonaktifkan", "disable" -> {
                val id = arguments["task_id"]?.toString()?.trim().orEmpty()
                val existing = taskDao.getById(id)
                    ?: return ToolExecutionResult(false, "Tugas dengan id='$id' tidak ditemukan.")
                taskDao.setEnabled(id, false)
                taskScheduler.cancelTask(id)
                ToolExecutionResult(true, "Tugas '${existing.title}' dinonaktifkan.")
            }
            "aktifkan", "enable" -> {
                val id = arguments["task_id"]?.toString()?.trim().orEmpty()
                val existing = taskDao.getById(id)
                    ?: return ToolExecutionResult(false, "Tugas dengan id='$id' tidak ditemukan.")
                val updated = existing.copy(enabled = true)
                taskDao.upsert(updated)
                taskScheduler.scheduleTask(updated)
                ToolExecutionResult(true, "Tugas '${existing.title}' diaktifkan kembali.")
            }
            else -> ToolExecutionResult(false, "Aksi tidak dikenal: '$action'. Gunakan buat, lihat, aktifkan, atau nonaktifkan.")
        }
    }
}
