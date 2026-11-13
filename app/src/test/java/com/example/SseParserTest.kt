package com.example

import com.example.llm.LlmStreamEvent
import com.example.llm.SseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {
    @Test
    fun `parses streaming text deltas tool calls and usage`() {
        val parser = SseParser()
        val e1 = parser.parseLine("""data: {"choices":[{"delta":{"content":"Halo "}}]}""")
        assertEquals(listOf(LlmStreamEvent.DeltaText("Halo ")), e1)

        val e2 = parser.parseLine(
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"buat_catatan","arguments":"{\"title\":"}}]}}]}"""
        )
        assertTrue(e2.isEmpty())

        val e3 = parser.parseLine(
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"Tes\"}"}}]},"finish_reason":"tool_calls"}]}"""
        )
        assertTrue(e3.first() is LlmStreamEvent.ToolCallsReady)
        val calls = (e3.first() as LlmStreamEvent.ToolCallsReady).calls
        assertEquals("buat_catatan", calls.first().name)
        assertEquals("""{"title":"Tes"}""", calls.first().argumentsJson)

        parser.parseLine("""data: {"usage":{"prompt_tokens":120,"completion_tokens":35,"total_tokens":155}}""")
        val doneEvents = parser.parseLine("data: [DONE]")
        assertTrue(doneEvents.any { it is LlmStreamEvent.Usage && it.usage.totalTokens == 155 })
        assertTrue(doneEvents.last() is LlmStreamEvent.Completed)
    }
}
