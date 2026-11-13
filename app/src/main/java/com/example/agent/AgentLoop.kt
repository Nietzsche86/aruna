package com.example.agent

import com.example.agent.model.AgentEvent
import com.example.agent.model.AgentStoredMessage
import com.example.agent.model.ExecutionMode
import com.example.agent.model.ToolExecutionContext
import com.example.llm.LlmConfig
import com.example.llm.LlmProvider
import com.example.llm.LlmStreamEvent
import com.example.llm.LlmToolCall
import com.example.llm.UsageInfo
import com.example.memory.MemoryExtractor
import com.example.memory.MemoryRepository
import com.example.memory.ProfileUpdater
import com.example.skills.SkillRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

interface AgentConversationStore {
    suspend fun getConversationSummary(conversationId: String): String?
    suspend fun getActiveMessages(conversationId: String): List<AgentStoredMessage>
    suspend fun getTurnCountSinceMemory(conversationId: String): Int
    suspend fun setTurnCountSinceMemory(conversationId: String, count: Int)
    suspend fun appendMessage(conversationId: String, message: AgentStoredMessage)
    suspend fun updateConversationTokens(conversationId: String, totalTokens: Int)
}

class AgentLoop(
    private val llmProvider: LlmProvider,
    private val promptBuilder: PromptBuilder,
    private val toolRegistry: ToolRegistry,
    private val approvalGate: ApprovalGate,
    private val auditLogger: ActionAuditLogger,
    private val memoryRepository: MemoryRepository,
    private val skillRepository: SkillRepository,
    private val memoryExtractor: MemoryExtractor,
    private val profileUpdater: ProfileUpdater,
    private val compactor: Compactor,
    private val conversationStore: AgentConversationStore,
) {
    fun run(
        conversationId: String,
        primaryConfig: LlmConfig,
        utilityConfig: LlmConfig,
        latestUserQuery: String,
        mode: ExecutionMode = ExecutionMode.FOREGROUND,
        taskId: String? = null,
    ): Flow<AgentEvent> = flow {
        var finalAssistantText = ""
        var latestTotalTokens = 0
        var pendingApprovalCreated = false

        val toolDefs = toolRegistry.getDefinitions()

        for (step in 1..mode.maxSteps) {
            val profile = memoryRepository.getProfile()
            val skills = skillRepository.getEnabledSkills()
            val memories = if (latestUserQuery.isNotBlank()) {
                memoryRepository.searchRelevantMemories(latestUserQuery, limit = 10)
            } else {
                memoryRepository.getActiveConfirmedMemories().take(8)
            }
            val summary = conversationStore.getConversationSummary(conversationId)
            val activeMessages = conversationStore.getActiveMessages(conversationId)

            val promptMessages = promptBuilder.buildPromptMessages(
                profile = profile,
                activeSkills = skills,
                relevantMemories = memories,
                conversationSummary = summary,
                activeMessages = activeMessages,
            )

            val textBuffer = StringBuilder()
            var receivedToolCalls: List<LlmToolCall> = emptyList()
            var streamUsage: UsageInfo? = null
            var streamError: String? = null

            llmProvider.streamChat(primaryConfig, promptMessages, toolDefs).collect { event ->
                when (event) {
                    is LlmStreamEvent.DeltaText -> {
                        textBuffer.append(event.text)
                        emit(AgentEvent.StreamingDelta(textBuffer.toString()))
                    }
                    is LlmStreamEvent.ToolCallsReady -> {
                        receivedToolCalls = event.calls
                    }
                    is LlmStreamEvent.Usage -> {
                        streamUsage = event.usage
                    }
                    is LlmStreamEvent.Error -> {
                        streamError = event.message
                    }
                    is LlmStreamEvent.Completed -> Unit
                }
            }

            if (streamError != null) {
                emit(AgentEvent.Error(streamError!!))
                return@flow
            }

            val assistantContent = textBuffer.toString()
            val resolvedUsage = TokenTracker.resolveUsage(streamUsage, promptMessages, assistantContent)
            latestTotalTokens = resolvedUsage.totalTokens
            conversationStore.updateConversationTokens(conversationId, latestTotalTokens)

            val assistantMsg = AgentStoredMessage(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = assistantContent,
                toolCalls = receivedToolCalls,
                promptTokens = resolvedUsage.promptTokens,
                completionTokens = resolvedUsage.completionTokens,
                createdAt = System.currentTimeMillis(),
            )
            conversationStore.appendMessage(conversationId, assistantMsg)

            if (receivedToolCalls.isEmpty()) {
                finalAssistantText = assistantContent
                break
            }

            // Execute requested tool calls
            var stopLoopForBackgroundApproval = false
            for (call in receivedToolCalls) {
                val tool = toolRegistry.get(call.name)
                if (tool == null) {
                    val missingResult = "Tool '${call.name}' tidak ditemukan."
                    conversationStore.appendMessage(
                        conversationId,
                        AgentStoredMessage(
                            id = UUID.randomUUID().toString(),
                            role = "tool",
                            content = missingResult,
                            toolCallId = call.id,
                            toolName = call.name,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                    continue
                }

                val parsedArgs = toolRegistry.parseArguments(call.argumentsJson)
                val argsSummary = tool.summarizeCall(parsedArgs)
                emit(AgentEvent.ToolExecuting(tool.name, argsSummary))

                val approval = approvalGate.evaluate(
                    tool = tool,
                    argumentsJson = call.argumentsJson,
                    argumentsSummary = argsSummary,
                    conversationId = conversationId,
                    taskId = taskId,
                    mode = mode,
                )

                when (approval) {
                    is ApprovalOutcome.DeferredToBackgroundQueue -> {
                        pendingApprovalCreated = true
                        stopLoopForBackgroundApproval = true
                        val note = "Aksi '${tool.name}' ($argsSummary) ditunda ke antrean persetujuan (id=${approval.approvalId})."
                        auditLogger.log(tool.name, call.argumentsJson, note, approved = false, executionMode = mode)
                        conversationStore.appendMessage(
                            conversationId,
                            AgentStoredMessage(
                                id = UUID.randomUUID().toString(),
                                role = "tool",
                                content = note,
                                toolCallId = call.id,
                                toolName = call.name,
                                createdAt = System.currentTimeMillis(),
                            )
                        )
                        finalAssistantText = if (assistantContent.isNotBlank()) {
                            "$assistantContent\n\n$note"
                        } else note
                    }
                    ApprovalOutcome.RejectedByUser -> {
                        val rejectedMsg = "Pengguna menolak eksekusi aksi '${tool.name}'."
                        auditLogger.log(tool.name, call.argumentsJson, rejectedMsg, approved = false, executionMode = mode)
                        emit(AgentEvent.ToolCompleted(tool.name, rejectedMsg, success = false))
                        conversationStore.appendMessage(
                            conversationId,
                            AgentStoredMessage(
                                id = UUID.randomUUID().toString(),
                                role = "tool",
                                content = rejectedMsg,
                                toolCallId = call.id,
                                toolName = call.name,
                                createdAt = System.currentTimeMillis(),
                            )
                        )
                    }
                    ApprovalOutcome.Approved -> {
                        val execResult = runCatching {
                            tool.execute(
                                parsedArgs,
                                ToolExecutionContext(
                                    conversationId = conversationId,
                                    taskId = taskId,
                                    executionMode = mode,
                                ),
                            )
                        }.getOrElse { ex ->
                            com.example.agent.model.ToolExecutionResult(
                                success = false,
                                output = "Error eksekusi tool: ${ex.message}",
                            )
                        }
                        auditLogger.log(
                            tool = tool.name,
                            argumentsJson = call.argumentsJson,
                            result = execResult.output,
                            approved = true,
                            executionMode = mode,
                        )
                        emit(AgentEvent.ToolCompleted(tool.name, execResult.output.take(180), execResult.success))
                        conversationStore.appendMessage(
                            conversationId,
                            AgentStoredMessage(
                                id = UUID.randomUUID().toString(),
                                role = "tool",
                                content = execResult.output,
                                toolCallId = call.id,
                                toolName = call.name,
                                createdAt = System.currentTimeMillis(),
                            )
                        )
                    }
                }
            }

            if (stopLoopForBackgroundApproval) break

            if (step == mode.maxSteps) {
                finalAssistantText = assistantContent.ifBlank {
                    "Batas langkah agen (${mode.maxSteps}) tercapai. Silakan lanjutkan jika masih ada yang perlu diproses."
                }
            }
        }

        // Auto-compact check at 70% context threshold
        if (TokenTracker.shouldAutoCompact(latestTotalTokens, primaryConfig.contextLimit)) {
            emit(AgentEvent.CompactingContext)
            runCatching { compactor.compact(conversationId, utilityConfig) }
        } else {
            // Periodic background memory extraction every N=10 turns
            val turns = conversationStore.getTurnCountSinceMemory(conversationId) + 1
            if (turns >= MEMORY_TURN_INTERVAL) {
                val recent = conversationStore.getActiveMessages(conversationId).map {
                    com.example.llm.LlmMessage(role = it.role, content = it.content)
                }
                val changes = runCatching {
                    memoryExtractor.extractAndApply(utilityConfig, recent)
                }.getOrDefault(0)
                if (changes > 0) {
                    runCatching { profileUpdater.refreshProfile(utilityConfig) }
                    emit(AgentEvent.MemorySynced(changes))
                }
                conversationStore.setTurnCountSinceMemory(conversationId, 0)
            } else {
                conversationStore.setTurnCountSinceMemory(conversationId, turns)
            }
        }

        emit(
            AgentEvent.Finished(
                finalText = finalAssistantText,
                totalSessionTokens = latestTotalTokens,
                pendingApprovalCreated = pendingApprovalCreated,
            )
        )
    }

    companion object {
        const val MEMORY_TURN_INTERVAL = 10
    }
}
