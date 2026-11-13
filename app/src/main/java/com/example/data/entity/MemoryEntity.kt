package com.example.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val text: String,
    val category: String,
    val tags: String = "",
    @ColumnInfo(name = "source_message_id") val sourceMessageId: String? = null,
    val enabled: Boolean = true,
    @ColumnInfo(name = "pending_confirmation") val pendingConfirmation: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Fts4(contentEntity = MemoryEntity::class)
@Entity(tableName = "memories_fts")
data class MemoryFtsEntity(
    val text: String,
    val category: String,
    val tags: String,
)

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: String = "default",
    @ColumnInfo(name = "summary_text") val summaryText: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
