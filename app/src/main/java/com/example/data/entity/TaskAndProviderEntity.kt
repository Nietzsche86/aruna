package com.example.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val prompt: String,
    val cron: String,
    val enabled: Boolean = true,
    @ColumnInfo(name = "use_exact_alarm") val useExactAlarm: Boolean = false,
    @ColumnInfo(name = "last_run_at") val lastRunAt: Long? = null,
    @ColumnInfo(name = "next_run_at") val nextRunAt: Long? = null,
    @ColumnInfo(name = "last_status") val lastStatus: String? = null,
)

@Entity(tableName = "pending_approvals")
data class PendingApprovalEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "task_id") val taskId: String? = null,
    @ColumnInfo(name = "conversation_id") val conversationId: String? = null,
    @ColumnInfo(name = "tool_name") val toolName: String,
    @ColumnInfo(name = "arguments_json") val argumentsJson: String,
    val summary: String,
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String = "OPENAI_COMPAT",
    @ColumnInfo(name = "base_url") val baseUrl: String,
    @ColumnInfo(name = "default_model") val defaultModel: String,
    @ColumnInfo(name = "utility_model") val utilityModel: String,
    @ColumnInfo(name = "supports_vision") val supportsVision: Boolean = false,
    @ColumnInfo(name = "context_limit") val contextLimit: Int = 32768,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "action_log")
data class ActionLogEntity(
    @PrimaryKey val id: String,
    val tool: String,
    @ColumnInfo(name = "arguments_json") val argumentsJson: String,
    val result: String,
    val approved: Boolean,
    @ColumnInfo(name = "execution_context") val executionContext: String, // FOREGROUND | BACKGROUND
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
