package com.example.data.repository

import com.example.data.FtsQueryHelper
import com.example.data.dao.MemoryDao
import com.example.data.dao.ProfileDao
import com.example.data.entity.MemoryEntity
import com.example.data.entity.ProfileEntity
import com.example.memory.MemoryItem
import com.example.memory.MemoryRepository
import com.example.memory.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MemoryRepositoryImpl(
    private val memoryDao: MemoryDao,
    private val profileDao: ProfileDao,
) : MemoryRepository {

    fun observeMemories(): Flow<List<MemoryItem>> =
        memoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeProfile(): Flow<UserProfile?> =
        profileDao.observeProfile().map { it?.let { p -> UserProfile(p.summaryText, p.updatedAt) } }

    override suspend fun getAllMemories(): List<MemoryItem> =
        memoryDao.getAll().map { it.toDomain() }

    override suspend fun getActiveConfirmedMemories(): List<MemoryItem> =
        memoryDao.getActiveConfirmed().map { it.toDomain() }

    override suspend fun searchRelevantMemories(query: String, limit: Int): List<MemoryItem> {
        val fts = FtsQueryHelper.toMatchQuery(query)
        val matched = if (fts.isNotBlank()) {
            runCatching { memoryDao.searchFts(fts, limit) }.getOrDefault(emptyList())
        } else emptyList()
        if (matched.isNotEmpty()) return matched.map { it.toDomain() }
        return memoryDao.getActiveConfirmed().take(limit).map { it.toDomain() }
    }

    override suspend fun addMemory(item: MemoryItem) {
        memoryDao.upsert(item.toEntity())
    }

    override suspend fun updateMemory(id: String, newText: String, category: String, tags: String) {
        val existing = memoryDao.getAll().firstOrNull { it.id == id } ?: return
        memoryDao.update(
            existing.copy(
                text = newText,
                category = category,
                tags = tags,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun deleteMemory(id: String) {
        memoryDao.deleteById(id)
    }

    suspend fun setMemoryEnabled(id: String, enabled: Boolean) {
        memoryDao.setEnabled(id, enabled, System.currentTimeMillis())
    }

    suspend fun confirmSensitiveMemory(id: String) {
        memoryDao.confirmMemory(id, System.currentTimeMillis())
    }

    override suspend fun getProfile(): UserProfile? =
        profileDao.getProfile()?.let { UserProfile(it.summaryText, it.updatedAt) }

    override suspend fun saveProfile(summaryText: String) {
        profileDao.upsert(ProfileEntity(summaryText = summaryText, updatedAt = System.currentTimeMillis()))
    }

    private fun MemoryEntity.toDomain() = MemoryItem(
        id = id,
        text = text,
        category = category,
        tags = tags,
        sourceMessageId = sourceMessageId,
        enabled = enabled,
        pendingConfirmation = pendingConfirmation,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun MemoryItem.toEntity() = MemoryEntity(
        id = id,
        text = text,
        category = category,
        tags = tags,
        sourceMessageId = sourceMessageId,
        enabled = enabled,
        pendingConfirmation = pendingConfirmation,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
