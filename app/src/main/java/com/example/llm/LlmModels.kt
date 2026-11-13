package com.example.llm

data class LlmConfig(
    val providerId: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val supportsVision: Boolean = false,
    val contextLimit: Int = 32768,
)

data class LlmToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

data class LlmMessage(
    val role: String, // "system", "user", "assistant", "tool"
    val content: String,
    val imageDataUrls: List<String> = emptyList(),
    val toolCalls: List<LlmToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
)

data class LlmToolDefinition(
    val name: String,
    val description: String,
    val parametersSchema: Map<String, Any>,
)

data class UsageInfo(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = promptTokens + completionTokens,
)

data class LlmChatResponse(
    val content: String,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val usage: UsageInfo? = null,
)

sealed interface LlmStreamEvent {
    data class DeltaText(val text: String) : LlmStreamEvent
    data class ToolCallsReady(val calls: List<LlmToolCall>) : LlmStreamEvent
    data class Usage(val usage: UsageInfo) : LlmStreamEvent
    data object Completed : LlmStreamEvent
    data class Error(val message: String) : LlmStreamEvent
}
