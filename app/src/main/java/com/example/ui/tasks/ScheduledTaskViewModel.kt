package com.example.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.agent.model.ExecutionMode
import com.example.agent.model.ToolExecutionContext
import com.example.data.entity.ActionLogEntity
import com.example.data.entity.NoteEntity
import com.example.data.entity.PendingApprovalEntity
import com.example.data.entity.ScheduledTaskEntity
import com.example.scheduler.CronParser
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class TasksUiState(
    val tasks: List<ScheduledTaskEntity> = emptyList(),
    val approvals: List<PendingApprovalEntity> = emptyList(),
    val actionLogs: List<ActionLogEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
)

class ScheduledTaskViewModel(private val container: AppContainer) : ViewModel() {
    val uiState: StateFlow<TasksUiState> = combine(
        container.taskAndApprovalRepository.observeTasks(),
        container.taskAndApprovalRepository.observeApprovals(),
        container.taskAndApprovalRepository.observeActionLogs(),
        container.taskAndApprovalRepository.observeNotes(),
    ) { tasks, approvals, logs, notes ->
        TasksUiState(tasks, approvals, logs, notes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TasksUiState())

    fun saveTask(id: String?, title: String, prompt: String, cron: String, useExactAlarm: Boolean): String? {
        if (title.isBlank() || prompt.isBlank()) return "Judul dan prompt tugas wajib diisi."
        if (!CronParser.isValid(cron)) return "Ekspresi cron tidak valid (gunakan 5 bagian, misal: 0 7 * * *)."

        viewModelScope.launch {
            val task = ScheduledTaskEntity(
                id = id ?: UUID.randomUUID().toString(),
                title = title.trim(),
                prompt = prompt.trim(),
                cron = cron.trim(),
                enabled = true,
                useExactAlarm = useExactAlarm,
            )
            container.database.scheduledTaskDao().upsert(task)
            container.taskScheduler.scheduleTask(task)
        }
        return null
    }

    fun toggleTask(task: ScheduledTaskEntity, enabled: Boolean) {
        viewModelScope.launch {
            val updated = task.copy(enabled = enabled)
            container.database.scheduledTaskDao().upsert(updated)
            if (enabled) container.taskScheduler.scheduleTask(updated)
            else container.taskScheduler.cancelTask(task.id)
        }
    }

    fun runTaskNow(taskId: String) {
        container.taskScheduler.enqueueImmediateWork(taskId)
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            container.taskScheduler.cancelTask(taskId)
            container.database.scheduledTaskDao().deleteById(taskId)
        }
    }

    fun decidePendingApproval(item: PendingApprovalEntity, approve: Boolean) {
        viewModelScope.launch {
            val approvalDao = container.database.pendingApprovalDao()
            container.notificationHelper.cancelNotification(item.id)
            if (approve) {
                approvalDao.updateStatus(item.id, "APPROVED")
                val tool = container.toolRegistry.get(item.toolName)
                val output = if (tool != null) {
                    val args = container.toolRegistry.parseArguments(item.argumentsJson)
                    tool.execute(
                        args,
                        ToolExecutionContext(
                            conversationId = item.conversationId ?: "",
                            taskId = item.taskId,
                            executionMode = ExecutionMode.FOREGROUND,
                        ),
                    ).output
                } else {
                    "Tool '${item.toolName}' tidak ditemukan."
                }
                container.database.actionLogDao().insert(
                    ActionLogEntity(
                        id = UUID.randomUUID().toString(),
                        tool = item.toolName,
                        argumentsJson = item.argumentsJson,
                        result = output,
                        approved = true,
                        executionContext = "BACKGROUND_UI_APPROVED",
                        createdAt = System.currentTimeMillis(),
                    )
                )
            } else {
                approvalDao.updateStatus(item.id, "REJECTED")
                container.database.actionLogDao().insert(
                    ActionLogEntity(
                        id = UUID.randomUUID().toString(),
                        tool = item.toolName,
                        argumentsJson = item.argumentsJson,
                        result = "Ditolak pengguna di antrean aplikasi",
                        approved = false,
                        executionContext = "BACKGROUND_UI_REJECTED",
                        createdAt = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch { container.database.noteDao().deleteById(noteId) }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ScheduledTaskViewModel(container) as T
        }
    }
}
