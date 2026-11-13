package com.example.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.AsistenApp
import com.example.agent.model.ExecutionMode
import com.example.agent.model.ToolExecutionContext
import com.example.data.entity.ActionLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class ApprovalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val approvalId = intent.getStringExtra(EXTRA_APPROVAL_ID) ?: return
        val pendingResult = goAsync()
        val app = context.applicationContext as AsistenApp
        val container = app.container

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val approvalDao = container.database.pendingApprovalDao()
                val item = approvalDao.getById(approvalId) ?: return@launch
                container.notificationHelper.cancelNotification(approvalId)

                when (intent.action) {
                    ACTION_APPROVE -> {
                        approvalDao.updateStatus(approvalId, "APPROVED")
                        val tool = container.toolRegistry.get(item.toolName)
                        val resultText = if (tool != null) {
                            val args = container.toolRegistry.parseArguments(item.argumentsJson)
                            val exec = tool.execute(
                                args,
                                ToolExecutionContext(
                                    conversationId = item.conversationId ?: "",
                                    taskId = item.taskId,
                                    executionMode = ExecutionMode.BACKGROUND,
                                ),
                            )
                            exec.output
                        } else {
                            "Tool '${item.toolName}' tidak ditemukan."
                        }
                        container.database.actionLogDao().insert(
                            ActionLogEntity(
                                id = UUID.randomUUID().toString(),
                                tool = item.toolName,
                                argumentsJson = item.argumentsJson,
                                result = resultText,
                                approved = true,
                                executionContext = "BACKGROUND",
                                createdAt = System.currentTimeMillis(),
                            )
                        )
                        container.notificationHelper.showTaskResult(
                            taskId = "approved_$approvalId",
                            title = "Aksi Disetujui: ${item.toolName}",
                            body = resultText,
                        )
                    }
                    ACTION_REJECT -> {
                        approvalDao.updateStatus(approvalId, "REJECTED")
                        container.database.actionLogDao().insert(
                            ActionLogEntity(
                                id = UUID.randomUUID().toString(),
                                tool = item.toolName,
                                argumentsJson = item.argumentsJson,
                                result = "Ditolak pengguna melalui notifikasi",
                                approved = false,
                                executionContext = "BACKGROUND",
                                createdAt = System.currentTimeMillis(),
                            )
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_APPROVE = "com.example.aruna.ACTION_APPROVE"
        const val ACTION_REJECT = "com.example.aruna.ACTION_REJECT"
        const val EXTRA_APPROVAL_ID = "extra_approval_id"
    }
}

class ExactTaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(TaskWorker.KEY_TASK_ID) ?: return
        val app = context.applicationContext as AsistenApp
        app.container.taskScheduler.enqueueImmediateWork(taskId)
    }
}
