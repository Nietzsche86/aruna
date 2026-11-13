package com.example.tools

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.example.agent.AgentTool
import com.example.agent.model.ExecutionMode
import com.example.agent.model.PermissionLevel
import com.example.agent.model.ToolExecutionContext
import com.example.agent.model.ToolExecutionResult
import com.example.scheduler.NotificationHelper

class AlarmTool(
    private val context: Context,
    private val notificationHelper: NotificationHelper,
) : AgentTool {
    override val name = "buat_alarm"
    override val description = "Menyetel alarm pada jam dan menit tertentu dengan label pengingat."
    override val permissionLevel = PermissionLevel.TANYA_DULU
    override val parametersSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "hour" to mapOf("type" to "integer", "description" to "Jam dalam format 24 jam (0-23)"),
            "minute" to mapOf("type" to "integer", "description" to "Menit (0-59)"),
            "label" to mapOf("type" to "string", "description" to "Label atau pesan alarm"),
        ),
        "required" to listOf("hour", "minute"),
    )

    override fun summarizeCall(arguments: Map<String, Any?>): String {
        val h = (arguments["hour"] as? Number)?.toInt()?.toString()?.padStart(2, '0') ?: "00"
        val m = (arguments["minute"] as? Number)?.toInt()?.toString()?.padStart(2, '0') ?: "00"
        val label = arguments["label"]?.toString() ?: "Alarm Aruna"
        return "Pasang alarm pukul $h:$m ($label)"
    }

    override suspend fun execute(
        arguments: Map<String, Any?>,
        context: ToolExecutionContext,
    ): ToolExecutionResult {
        val hour = (arguments["hour"] as? Number)?.toInt()?.coerceIn(0, 23)
            ?: return ToolExecutionResult(false, "Parameter 'hour' (0-23) wajib diisi.")
        val minute = (arguments["minute"] as? Number)?.toInt()?.coerceIn(0, 59)
            ?: return ToolExecutionResult(false, "Parameter 'minute' (0-59) wajib diisi.")
        val label = arguments["label"]?.toString()?.takeIf { it.isNotBlank() } ?: "Alarm Aruna"

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, context.executionMode == ExecutionMode.BACKGROUND)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val launched = runCatching {
            this.context.startActivity(intent)
        }.isSuccess

        val timeText = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        return if (launched) {
            ToolExecutionResult(true, "Alarm '$label' untuk pukul $timeText berhasil diteruskan ke aplikasi Jam.")
        } else {
            notificationHelper.showTaskResult(
                taskId = "alarm_${hour}_$minute",
                title = "Pengingat Alarm $timeText",
                body = label,
            )
            ToolExecutionResult(true, "Aplikasi Jam sistem tidak merespons langsung; pengingat notifikasi untuk $timeText ($label) telah dibuat.")
        }
    }
}
