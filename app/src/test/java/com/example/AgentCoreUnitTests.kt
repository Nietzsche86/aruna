package com.example

import com.example.agent.ApprovalGate
import com.example.agent.ApprovalOutcome
import com.example.agent.PromptBuilder
import com.example.agent.ToolRegistry
import com.example.agent.model.AgentStoredMessage
import com.example.agent.model.ExecutionMode
import com.example.agent.model.PermissionLevel
import com.example.agent.model.ToolExecutionContext
import com.example.agent.model.ToolExecutionResult
import com.example.files.TextChunker
import com.example.llm.LlmConfig
import com.example.llm.LlmMessage
import com.example.llm.LlmProvider
import com.example.llm.LlmStreamEvent
import com.example.llm.LlmChatResponse
import com.example.memory.MemoryActionType
import com.example.memory.MemoryExtractor
import com.example.memory.MemoryItem
import com.example.memory.MemoryRepository
import com.example.memory.UserProfile
import com.example.skills.SkillItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentCoreUnitTests {

    @Test
    fun `promptBuilder places static prefix before dynamic memory and summary`() {
        val builder = PromptBuilder()
        val msgs = builder.buildPromptMessages(
            profile = UserProfile("Pengguna adalah developer Android di Jakarta."),
            activeSkills = listOf(
                SkillItem(
                    id = "s1",
                    name = "Ringkas Berita",
                    whenToUse = "Saat diminta merangkum berita pagi",
                    steps = "1. Ambil poin utama",
                )
            ),
            relevantMemories = listOf(
                MemoryItem(id = "m1", text = "Suka kopi hitam tanpa gula", category = "preferensi")
            ),
            conversationSummary = "Membahas setup Kotlin.",
            activeMessages = listOf(
                AgentStoredMessage(id = "1", role = "user", content = "Halo Aruna")
            ),
        )
        assertEquals("system", msgs[0].role)
        assertTrue(msgs[0].content.contains("Profil Pengguna Ringkas"))
        assertTrue(msgs[0].content.contains("Ringkas Berita"))
        assertEquals("system", msgs[1].role)
        assertTrue(msgs[1].content.contains("Suka kopi hitam tanpa gula"))
        assertTrue(msgs[1].content.contains("Membahas setup Kotlin"))
        assertEquals("user", msgs.last().role)
        assertEquals("Halo Aruna", msgs.last().content)
    }

    @Test
    fun `textChunker splits and ranks relevant chunks`() {
        val bigText = ("Alpha topic about Kotlin Coroutines. ".repeat(60)) +
            ("Beta topic about Room FTS4 indexing. ".repeat(60))
        val chunks = TextChunker.chunk(bigText, chunkSize = 800, overlap = 100)
        assertTrue(chunks.size >= 3)
        val found = TextChunker.searchChunks(bigText, "Room FTS4", maxChunks = 1)
        assertTrue(found.first().content.contains("FTS4"))
    }

    @Test
    fun `memoryExtractor parses ADD UPDATE DELETE operations and flags sensitive categories`() {
        val dummyProvider = object : LlmProvider {
            override fun streamChat(config: LlmConfig, messages: List<LlmMessage>, tools: List<com.example.llm.LlmToolDefinition>): Flow<LlmStreamEvent> = emptyFlow()
            override suspend fun chat(config: LlmConfig, messages: List<LlmMessage>, tools: List<com.example.llm.LlmToolDefinition>) = LlmChatResponse("")
            override suspend fun listModels(baseUrl: String, apiKey: String) = emptyList<String>()
        }
        val dummyRepo = object : MemoryRepository {
            override suspend fun getAllMemories() = emptyList<MemoryItem>()
            override suspend fun getActiveConfirmedMemories() = emptyList<MemoryItem>()
            override suspend fun searchRelevantMemories(query: String, limit: Int) = emptyList<MemoryItem>()
            override suspend fun addMemory(item: MemoryItem) {}
            override suspend fun updateMemory(id: String, newText: String, category: String, tags: String) {}
            override suspend fun deleteMemory(id: String) {}
            override suspend fun getProfile(): UserProfile? = null
            override suspend fun saveProfile(summaryText: String) {}
        }
        val extractor = MemoryExtractor(dummyProvider, dummyRepo)
        val ops = extractor.parseOperations(
            """
            ```json
            {
              "operations": [
                {"action": "ADD", "category": "kesehatan", "text": "Alergi udang", "tags": "alergi"},
                {"action": "UPDATE", "id": "m1", "category": "pekerjaan", "text": "Pindah ke Bandung"},
                {"action": "DELETE", "id": "m2"}
              ]
            }
            ```
            """.trimIndent()
        )
        assertEquals(3, ops.size)
        assertEquals(MemoryActionType.ADD, ops[0].action)
        assertTrue(ops[0].sensitive)
        assertEquals(MemoryActionType.UPDATE, ops[1].action)
        assertEquals("m1", ops[1].id)
        assertEquals(MemoryActionType.DELETE, ops[2].action)
    }

    @Test
    fun `approvalGate defers TANYA_DULU to queue in background mode`() = runTest {
        var queuedSummary = ""
        val gate = ApprovalGate(
            foregroundHandler = { false },
            pendingSink = { req ->
                queuedSummary = req.summary
                "pending-123"
            },
        )
        val riskyTool = object : com.example.agent.AgentTool {
            override val name = "buat_event_kalender"
            override val description = "Buat event"
            override val permissionLevel = PermissionLevel.TANYA_DULU
            override val parametersSchema = emptyMap<String, Any>()
            override suspend fun execute(arguments: Map<String, Any?>, context: ToolExecutionContext) =
                ToolExecutionResult(true, "ok")
        }
        val outcome = gate.evaluate(
            tool = riskyTool,
            argumentsJson = "{}",
            argumentsSummary = "Meeting besok",
            conversationId = "c1",
            taskId = "t1",
            mode = ExecutionMode.BACKGROUND,
        )
        assertTrue(outcome is ApprovalOutcome.DeferredToBackgroundQueue)
        assertEquals("Meeting besok", queuedSummary)
    }
}
