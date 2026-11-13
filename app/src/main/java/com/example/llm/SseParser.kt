package com.example.llm

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class SseParser(
    moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
) {
    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    private val mapAdapter = moshi.adapter<Map<String, Any>>(mapType)

    private val toolBuffers = linkedMapOf<Int, MutableToolBuffer>()
    private var lastUsage: UsageInfo? = null

    private data class MutableToolBuffer(
        var id: String = "",
        var name: String = "",
        val args: StringBuilder = StringBuilder(),
    )

    fun reset() {
        toolBuffers.clear()
        lastUsage = null
    }

    /**
     * Parses a single SSE line (e.g., "data: {...}" or "data: [DONE]").
     * Returns emitted stream events in order.
     */
    @Suppress("UNCHECKED_CAST")
    fun parseLine(rawLine: String): List<LlmStreamEvent> {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith(":")) return emptyList()
        if (!line.startsWith("data:")) return emptyList()

        val payload = line.removePrefix("data:").trim()
        if (payload == "[DONE]") {
            val events = mutableListOf<LlmStreamEvent>()
            val calls = flushToolCalls()
            if (calls.isNotEmpty()) {
                events.add(LlmStreamEvent.ToolCallsReady(calls))
            }
            lastUsage?.let { events.add(LlmStreamEvent.Usage(it)) }
            events.add(LlmStreamEvent.Completed)
            return events
        }

        val json = runCatching { mapAdapter.fromJson(payload) }.getOrNull() ?: return emptyList()
        val events = mutableListOf<LlmStreamEvent>()

        // Check inline error object from OpenAI-compat providers
        val errorMap = json["error"] as? Map<String, Any?>
        if (errorMap != null) {
            val msg = errorMap["message"]?.toString() ?: "Provider error"
            events.add(LlmStreamEvent.Error(msg))
            return events
        }

        // Parse usage if present
        val usageMap = json["usage"] as? Map<String, Any?>
        if (usageMap != null) {
            val prompt = (usageMap["prompt_tokens"] as? Number)?.toInt() ?: 0
            val completion = (usageMap["completion_tokens"] as? Number)?.toInt() ?: 0
            val total = (usageMap["total_tokens"] as? Number)?.toInt() ?: (prompt + completion)
            lastUsage = UsageInfo(prompt, completion, total)
        }

        val choices = json["choices"] as? List<Map<String, Any?>>
        val firstChoice = choices?.firstOrNull()
        val delta = firstChoice?.get("delta") as? Map<String, Any?>
        if (delta != null) {
            val content = delta["content"] as? String
            if (!content.isNullOrEmpty()) {
                events.add(LlmStreamEvent.DeltaText(content))
            }

            val deltaToolCalls = delta["tool_calls"] as? List<Map<String, Any?>>
            deltaToolCalls?.forEach { tc ->
                val index = (tc["index"] as? Number)?.toInt() ?: 0
                val buffer = toolBuffers.getOrPut(index) { MutableToolBuffer() }
                (tc["id"] as? String)?.takeIf { it.isNotBlank() }?.let { buffer.id = it }
                val fn = tc["function"] as? Map<String, Any?>
                if (fn != null) {
                    (fn["name"] as? String)?.takeIf { it.isNotBlank() }?.let { buffer.name += it }
                    (fn["arguments"] as? String)?.let { buffer.args.append(it) }
                }
            }
        }

        val finishReason = firstChoice?.get("finish_reason") as? String
        if (finishReason == "tool_calls") {
            val calls = flushToolCalls()
            if (calls.isNotEmpty()) {
                events.add(LlmStreamEvent.ToolCallsReady(calls))
            }
        }

        return events
    }

    fun flushPendingEventsOnEof(): List<LlmStreamEvent> {
        val events = mutableListOf<LlmStreamEvent>()
        val calls = flushToolCalls()
        if (calls.isNotEmpty()) {
            events.add(LlmStreamEvent.ToolCallsReady(calls))
        }
        lastUsage?.let { events.add(LlmStreamEvent.Usage(it)) }
        return events
    }

    private fun flushToolCalls(): List<LlmToolCall> {
        if (toolBuffers.isEmpty()) return emptyList()
        val calls = toolBuffers.entries
            .sortedBy { it.key }
            .mapNotNull { (_, buf) ->
                if (buf.name.isBlank()) null
                else LlmToolCall(
                    id = buf.id.ifBlank { "call_${buf.name}_${System.nanoTime()}" },
                    name = buf.name,
                    argumentsJson = buf.args.toString().ifBlank { "{}" },
                )
            }
        toolBuffers.clear()
        return calls
    }
}
