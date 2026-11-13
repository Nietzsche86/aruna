package com.example.agent

import com.example.llm.LlmMessage
import com.example.llm.UsageInfo

object TokenTracker {
    const val COMPACT_THRESHOLD_RATIO = 0.70

    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return (text.length / 4).coerceAtLeast(1)
    }

    fun estimateMessagesTokens(messages: List<LlmMessage>): Int =
        messages.sumOf { estimateTokens(it.content) + (it.imageDataUrls.size * 600) + 8 }

    fun resolveUsage(
        apiUsage: UsageInfo?,
        promptMessages: List<LlmMessage>,
        completionText: String,
    ): UsageInfo {
        if (apiUsage != null && (apiUsage.promptTokens > 0 || apiUsage.completionTokens > 0)) {
            return apiUsage
        }
        val promptEst = estimateMessagesTokens(promptMessages)
        val completionEst = estimateTokens(completionText)
        return UsageInfo(
            promptTokens = promptEst,
            completionTokens = completionEst,
            totalTokens = promptEst + completionEst,
        )
    }

    fun shouldAutoCompact(usedTokens: Int, contextLimit: Int): Boolean {
        if (contextLimit <= 0) return false
        return usedTokens >= (contextLimit * COMPACT_THRESHOLD_RATIO).toInt()
    }
}
