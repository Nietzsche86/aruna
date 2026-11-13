package com.example.memory

data class MemoryItem(
    val id: String,
    val text: String,
    val category: String,
    val tags: String = "",
    val sourceMessageId: String? = null,
    val enabled: Boolean = true,
    val pendingConfirmation: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class UserProfile(
    val summaryText: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class MemoryActionType {
    ADD,
    UPDATE,
    DELETE,
}

data class MemoryOperation(
    val action: MemoryActionType,
    val id: String? = null,
    val text: String = "",
    val category: String = "preferensi",
    val tags: String = "",
    val sensitive: Boolean = false,
)

interface MemoryRepository {
    suspend fun getAllMemories(): List<MemoryItem>
    suspend fun getActiveConfirmedMemories(): List<MemoryItem>
    suspend fun searchRelevantMemories(query: String, limit: Int = 10): List<MemoryItem>
    suspend fun addMemory(item: MemoryItem)
    suspend fun updateMemory(id: String, newText: String, category: String, tags: String)
    suspend fun deleteMemory(id: String)
    suspend fun getProfile(): UserProfile?
    suspend fun saveProfile(summaryText: String)
}
