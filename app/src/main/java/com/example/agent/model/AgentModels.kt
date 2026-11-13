package com.example.agent.model

import com.example.llm.LlmToolCall

enum class ExecutionMode(val maxSteps: Int) {
    FOREGROUND(maxSteps = 8),
    BACKGROUND(maxSteps = 4),
}

enum class PermissionLevel {
    BACA_SAJA,
    TANYA_DULU,
    OTOMATIS,
}

data class ToolExecutionContext(
    val conversationId: String,
    val taskId: String? = null,
    val executionMode: ExecutionMode,
)

data class ToolExecutionResult(
    val success: Boolean,
    val output: String,
)

data class AgentStoredMessage(
    val id: String,
    val role: String,
    val content: String,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val imageDataUrls: List<String> = emptyList(),
    val documentContextNote: String? = null,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val isCompacted: Boolean = false,
    val createdAt: Long = 0L,
)

sealed interface AgentEvent {
    data class StreamingDelta(val fullTextSoFar: String) : AgentEvent
    data class ToolExecuting(val toolName: String, val argumentsSummary: String) : AgentEvent
    data class ToolCompleted(val toolName: String, val resultPreview: String, val success: Boolean) : AgentEvent
    data class WaitingApproval(val toolName: String, val summary: String, val argumentsJson: String) : AgentEvent
    data object CompactingContext : AgentEvent
    data class MemorySynced(val changesCount: Int) : AgentEvent
    data class Finished(
        val finalText: String,
        val totalSessionTokens: Int,
        val pendingApprovalCreated: Boolean = false,
    ) : AgentEvent
    data class Error(val message: String) : AgentEvent
}
