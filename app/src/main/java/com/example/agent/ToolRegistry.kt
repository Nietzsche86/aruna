package com.example.agent

import com.example.agent.model.PermissionLevel
import com.example.agent.model.ToolExecutionContext
import com.example.agent.model.ToolExecutionResult
import com.example.llm.LlmToolDefinition
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

interface AgentTool {
    val name: String
    val description: String
    val permissionLevel: PermissionLevel
    val parametersSchema: Map<String, Any>

    fun summarizeCall(arguments: Map<String, Any?>): String = "$name(${arguments.entries.joinToString { "${it.key}=${it.value}" }})"

    suspend fun execute(
        arguments: Map<String, Any?>,
        context: ToolExecutionContext,
    ): ToolExecutionResult
}

class ToolRegistry(
    tools: List<AgentTool> = emptyList(),
    moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
) {
    private val toolMap = linkedMapOf<String, AgentTool>()
    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(mapType)

    init {
        tools.forEach { register(it) }
    }

    fun register(tool: AgentTool) {
        toolMap[tool.name] = tool
    }

    fun get(name: String): AgentTool? = toolMap[name]

    fun getDefinitions(): List<LlmToolDefinition> = toolMap.values.map {
        LlmToolDefinition(
            name = it.name,
            description = it.description,
            parametersSchema = it.parametersSchema,
        )
    }

    fun parseArguments(argumentsJson: String): Map<String, Any?> {
        if (argumentsJson.isBlank()) return emptyMap()
        return runCatching { mapAdapter.fromJson(argumentsJson) ?: emptyMap() }.getOrDefault(emptyMap())
    }
}
