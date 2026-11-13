package com.example.llm

import kotlinx.coroutines.flow.Flow

interface LlmProvider {
    fun streamChat(
        config: LlmConfig,
        messages: List<LlmMessage>,
        tools: List<LlmToolDefinition> = emptyList(),
    ): Flow<LlmStreamEvent>

    suspend fun chat(
        config: LlmConfig,
        messages: List<LlmMessage>,
        tools: List<LlmToolDefinition> = emptyList(),
    ): LlmChatResponse

    suspend fun listModels(baseUrl: String, apiKey: String): List<String>
}
