package com.example.llm

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OpenAiCompatProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
) : LlmProvider {

    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    private val mapAdapter = moshi.adapter<Map<String, Any>>(mapType)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun streamChat(
        config: LlmConfig,
        messages: List<LlmMessage>,
        tools: List<LlmToolDefinition>,
    ): Flow<LlmStreamEvent> = flow {
        val url = normalizeBaseUrl(config.baseUrl) + "/chat/completions"
        val bodyWithUsage = buildRequestJson(config, messages, tools, stream = true, includeUsage = true)

        var response = executePost(url, config.apiKey, bodyWithUsage)
        // Some OpenAI-compatible endpoints (e.g. older Ollama/LM Studio) reject stream_options
        if (response.code == 400) {
            response.close()
            val bodySimple = buildRequestJson(config, messages, tools, stream = true, includeUsage = false)
            response = executePost(url, config.apiKey, bodySimple)
        }

        response.use { res ->
            if (!res.isSuccessful) {
                val errBody = res.body?.string().orEmpty().take(300)
                emit(LlmStreamEvent.Error("HTTP ${res.code}: ${errBody.ifBlank { res.message }}"))
                return@flow
            }
            val source = res.body?.source()
            if (source == null) {
                emit(LlmStreamEvent.Error("Respons kosong dari provider"))
                return@flow
            }
            val parser = SseParser()
            var completedEmitted = false
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                val events = parser.parseLine(line)
                for (event in events) {
                    if (event is LlmStreamEvent.Completed) completedEmitted = true
                    emit(event)
                }
            }
            if (!completedEmitted) {
                parser.flushPendingEventsOnEof().forEach { emit(it) }
                emit(LlmStreamEvent.Completed)
            }
        }
    }.flowOn(Dispatchers.IO)

    @Suppress("UNCHECKED_CAST")
    override suspend fun chat(
        config: LlmConfig,
        messages: List<LlmMessage>,
        tools: List<LlmToolDefinition>,
    ): LlmChatResponse = withContext(Dispatchers.IO) {
        val url = normalizeBaseUrl(config.baseUrl) + "/chat/completions"
        val body = buildRequestJson(config, messages, tools, stream = false, includeUsage = false)
        executePost(url, config.apiKey, body).use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw IllegalStateException("HTTP ${res.code}: ${raw.take(300)}")
            }
            val json = mapAdapter.fromJson(raw)
                ?: throw IllegalStateException("Format JSON respons tidak valid")
            val choices = json["choices"] as? List<Map<String, Any?>>
            val message = choices?.firstOrNull()?.get("message") as? Map<String, Any?>
            val content = message?.get("content")?.toString().orEmpty()
            val toolCallsRaw = message?.get("tool_calls") as? List<Map<String, Any?>>
            val toolCalls = toolCallsRaw?.mapNotNull { tc ->
                val fn = tc["function"] as? Map<String, Any?> ?: return@mapNotNull null
                LlmToolCall(
                    id = tc["id"]?.toString() ?: "call_${System.nanoTime()}",
                    name = fn["name"]?.toString() ?: return@mapNotNull null,
                    argumentsJson = fn["arguments"]?.toString() ?: "{}",
                )
            } ?: emptyList()

            val usageMap = json["usage"] as? Map<String, Any?>
            val usage = usageMap?.let {
                val p = (it["prompt_tokens"] as? Number)?.toInt() ?: 0
                val c = (it["completion_tokens"] as? Number)?.toInt() ?: 0
                val t = (it["total_tokens"] as? Number)?.toInt() ?: (p + c)
                UsageInfo(p, c, t)
            }
            LlmChatResponse(content = content, toolCalls = toolCalls, usage = usage)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun listModels(baseUrl: String, apiKey: String): List<String> =
        withContext(Dispatchers.IO) {
            val url = normalizeBaseUrl(baseUrl) + "/models"
            val reqBuilder = Request.Builder().url(url).get()
            if (apiKey.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
            }
            client.newCall(reqBuilder.build()).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    throw IllegalStateException("Gagal mengambil daftar model (HTTP ${res.code})")
                }
                val json = mapAdapter.fromJson(raw) ?: return@withContext emptyList()
                val data = json["data"] as? List<Map<String, Any?>> ?: return@withContext emptyList()
                data.mapNotNull { it["id"]?.toString() }.sorted()
            }
        }

    private fun executePost(url: String, apiKey: String, jsonBody: String): okhttp3.Response {
        val reqBuilder = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
        if (apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
        }
        return client.newCall(reqBuilder.build()).execute()
    }

    internal fun buildRequestJson(
        config: LlmConfig,
        messages: List<LlmMessage>,
        tools: List<LlmToolDefinition>,
        stream: Boolean,
        includeUsage: Boolean,
    ): String {
        val mappedMessages = messages.map { msg ->
            val obj = linkedMapOf<String, Any>("role" to msg.role)
            if (msg.role == "user" && msg.imageDataUrls.isNotEmpty() && config.supportsVision) {
                val parts = mutableListOf<Map<String, Any>>()
                if (msg.content.isNotBlank()) {
                    parts.add(mapOf("type" to "text", "text" to msg.content))
                }
                msg.imageDataUrls.forEach { dataUrl ->
                    parts.add(
                        mapOf(
                            "type" to "image_url",
                            "image_url" to mapOf("url" to dataUrl),
                        )
                    )
                }
                obj["content"] = parts
            } else {
                obj["content"] = msg.content
            }

            if (msg.toolCalls.isNotEmpty()) {
                obj["tool_calls"] = msg.toolCalls.map { tc ->
                    mapOf(
                        "id" to tc.id,
                        "type" to "function",
                        "function" to mapOf(
                            "name" to tc.name,
                            "arguments" to tc.argumentsJson,
                        ),
                    )
                }
            }
            if (msg.toolCallId != null) {
                obj["tool_call_id"] = msg.toolCallId
            }
            if (msg.toolName != null && msg.role == "tool") {
                obj["name"] = msg.toolName
            }
            obj
        }

        val payload = linkedMapOf<String, Any>(
            "model" to config.model,
            "messages" to mappedMessages,
            "stream" to stream,
        )
        if (stream && includeUsage) {
            payload["stream_options"] = mapOf("include_usage" to true)
        }
        if (tools.isNotEmpty()) {
            payload["tools"] = tools.map { tool ->
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to tool.name,
                        "description" to tool.description,
                        "parameters" to tool.parametersSchema,
                    ),
                )
            }
        }
        return mapAdapter.toJson(payload)
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return if (trimmed.endsWith("/v1")) trimmed else "$trimmed/v1"
    }
}
