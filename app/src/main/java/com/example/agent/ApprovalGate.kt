package com.example.agent

import com.example.agent.model.ExecutionMode
import com.example.agent.model.PermissionLevel

sealed interface ApprovalOutcome {
    data object Approved : ApprovalOutcome
    data object RejectedByUser : ApprovalOutcome
    data class DeferredToBackgroundQueue(val approvalId: String) : ApprovalOutcome
}

data class ApprovalRequest(
    val toolName: String,
    val summary: String,
    val argumentsJson: String,
    val conversationId: String,
    val taskId: String? = null,
)

fun interface ForegroundApprovalHandler {
    suspend fun requestApproval(request: ApprovalRequest): Boolean
}

fun interface PendingApprovalSink {
    suspend fun enqueue(request: ApprovalRequest): String
}

fun interface ActionAuditLogger {
    suspend fun log(
        tool: String,
        argumentsJson: String,
        result: String,
        approved: Boolean,
        executionMode: ExecutionMode,
    )
}

class ApprovalGate(
    private val foregroundHandler: ForegroundApprovalHandler,
    private val pendingSink: PendingApprovalSink,
) {
    suspend fun evaluate(
        tool: AgentTool,
        argumentsJson: String,
        argumentsSummary: String,
        conversationId: String,
        taskId: String?,
        mode: ExecutionMode,
    ): ApprovalOutcome {
        return when (tool.permissionLevel) {
            PermissionLevel.BACA_SAJA,
            PermissionLevel.OTOMATIS -> ApprovalOutcome.Approved
            PermissionLevel.TANYA_DULU -> {
                val request = ApprovalRequest(
                    toolName = tool.name,
                    summary = argumentsSummary,
                    argumentsJson = argumentsJson,
                    conversationId = conversationId,
                    taskId = taskId,
                )
                when (mode) {
                    ExecutionMode.FOREGROUND -> {
                        val granted = foregroundHandler.requestApproval(request)
                        if (granted) ApprovalOutcome.Approved else ApprovalOutcome.RejectedByUser
                    }
                    ExecutionMode.BACKGROUND -> {
                        val id = pendingSink.enqueue(request)
                        ApprovalOutcome.DeferredToBackgroundQueue(id)
                    }
                }
            }
        }
    }
}
