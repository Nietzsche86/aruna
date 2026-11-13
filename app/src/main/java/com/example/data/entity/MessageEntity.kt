package com.example.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversation_id", "created_at"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    val role: String, // "user", "assistant", "tool", "system"
    val content: String,
    @ColumnInfo(name = "tool_calls_json") val toolCallsJson: String? = null,
    @ColumnInfo(name = "tool_call_id") val toolCallId: String? = null,
    @ColumnInfo(name = "tool_name") val toolName: String? = null,
    @ColumnInfo(name = "prompt_tokens") val promptTokens: Int = 0,
    @ColumnInfo(name = "completion_tokens") val completionTokens: Int = 0,
    @ColumnInfo(name = "is_compacted") val isCompacted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Fts4(contentEntity = MessageEntity::class)
@Entity(tableName = "messages_fts")
data class MessageFtsEntity(
    val content: String,
)
