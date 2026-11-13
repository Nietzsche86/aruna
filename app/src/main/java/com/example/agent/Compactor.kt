package com.example.agent

import com.example.agent.model.AgentStoredMessage
import com.example.llm.LlmConfig
import com.example.llm.LlmMessage
import com.example.memory.ConversationSummarizer
import com.example.memory.MemoryExtractor
import com.example.memory.ProfileUpdater

interface CompactionPersistence {
    suspend fun getConversationSummary(conversationId: String): String?
    suspend fun getActiveMessages(conversationId: String): List<AgentStoredMessage>
    suspend fun saveCompactionResult(
        conversationId: String,
        newSummary: String,
        compactedMessageIds: List<String>,
        estimatedRemainingTokens: Int,
    )
}

class Compactor(
    private val memoryExtractor: MemoryExtractor,
    private val summarizer: ConversationSummarizer,
    private val profileUpdater: ProfileUpdater,
    private val persistence: CompactionPersistence,
) {
    /**
     * Compacts older active messages into conversations.summary while keeping the last [keepLastK] messages intact.
     * Always extracts memories first so no important facts are lost before trimming.
     */
    suspend fun compact(
        conversationId: String,
        utilityConfig: LlmConfig,
        keepLastK: Int = DEFAULT_KEEP_LAST_K,
    ): Boolean {
        val activeMessages = persistence.getActiveMessages(conversationId)
        if (activeMessages.size <= keepLastK) return false

        val toCompact = activeMessages.dropLast(keepLastK)
        val remaining = activeMessages.takeLast(keepLastK)
        val toCompactLlm = toCompact.map {
            LlmMessage(role = it.role, content = it.content)
        }

        // Step 1: Extract memories before trimming old messages
        val extracted = runCatching {
            memoryExtractor.extractAndApply(
                utilityConfig = utilityConfig,
                recentMessages = toCompactLlm,
                sourceMessageId = toCompact.lastOrNull()?.id,
            )
        }.getOrDefault(0)

        if (extracted > 0) {
            runCatching { profileUpdater.refreshProfile(utilityConfig) }
        }

        // Step 2: Summarize compacted messages into conversations.summary
        val existingSummary = persistence.getConversationSummary(conversationId)
        val newSummary = summarizer.summarize(
            utilityConfig = utilityConfig,
            existingSummary = existingSummary,
            messagesToCompact = toCompactLlm,
        )

        val remainingTokens = TokenTracker.estimateTokens(newSummary) +
            remaining.sumOf { TokenTracker.estimateTokens(it.content) + 8 }

        persistence.saveCompactionResult(
            conversationId = conversationId,
            newSummary = newSummary,
            compactedMessageIds = toCompact.map { it.id },
            estimatedRemainingTokens = remainingTokens,
        )
        return true
    }

    companion object {
        const val DEFAULT_KEEP_LAST_K = 6
    }
}
