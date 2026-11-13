package com.example.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "provider_id") val providerId: String,
    val model: String,
    val summary: String? = null,
    @ColumnInfo(name = "total_tokens") val totalTokens: Int = 0,
    @ColumnInfo(name = "turn_count_since_memory") val turnCountSinceMemory: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
