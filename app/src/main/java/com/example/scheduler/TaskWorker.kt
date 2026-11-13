package com.example.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.AsistenApp
import com.example.agent.model.AgentEvent
import com.example.agent.model.AgentStoredMessage
import com.example.agent.model.ExecutionMode
import kotlinx.coroutines.withTimeout
import java.util.UUID

class TaskWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val container = (applicationContext as AsistenApp).container
        val taskDao = container.database.scheduledTaskDao()
        val task = taskDao.getById(taskId) ?: return Result.success()
        if (!task.enabled) return Result.success()

        val provider = container.providerRepository.getActiveProvider()
        if (provider == null) {
            taskDao.upsert(task.copy(lastRunAt = System.currentTimeMillis(), lastStatus = "Gagal: Provider belum dikonfigurasi"))
            return Result.success()
        }

        val primaryConfig = container.providerRepository.toPrimaryLlmConfig(provider)
        val utilityConfig = container.providerRepository.toUtilityLlmConfig(provider)

        var outputSummary = ""
        var errorMsg: String? = null
        var pendingApproval = false

        try {
            // 3-minute internal timeout to finish cleanly before Android OS kills the worker
            withTimeout(180_000L) {
                val conv = container.chatRepository.createConversation(
                    title = "Tugas: ${task.title}",
                    providerId = provider.id,
                    model = provider.defaultModel,
                    carrySummaryFromLastSession = false,
                )
                container.chatRepository.appendMessage(
                    conv.id,
                    AgentStoredMessage(
                        id = UUID.randomUUID().toString(),
                        role = "user",
                        content = task.prompt,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                container.agentLoop.run(
                    conversationId = conv.id,
                    primaryConfig = primaryConfig,
                    utilityConfig = utilityConfig,
                    latestUserQuery = task.prompt,
                    mode = ExecutionMode.BACKGROUND,
                    taskId = task.id,
                ).collect { event ->
                    when (event) {
                        is AgentEvent.Finished -> {
                            outputSummary = event.finalText
                            pendingApproval = event.pendingApprovalCreated
                        }
                        is AgentEvent.Error -> errorMsg = event.message
                        else -> Unit
                    }
                }
            }
        } catch (e: Exception) {
            errorMsg = e.message ?: "Timeout / kesalahan eksekusi tugas"
        }

        val now = System.currentTimeMillis()
        val statusText = when {
            errorMsg != null -> "Error: $errorMsg"
            pendingApproval -> "Menunggu persetujuan pengguna"
            else -> "Sukses"
        }
        taskDao.upsert(task.copy(lastRunAt = now, lastStatus = statusText))

        // Schedule next cron run
        container.taskScheduler.scheduleTask(taskDao.getById(task.id) ?: task)

        val notifBody = errorMsg ?: outputSummary.ifBlank { "Tugas selesai dijalankan." }
        if (!pendingApproval) {
            container.notificationHelper.showTaskResult(task.id, task.title, notifBody)
        }
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
    }
}
