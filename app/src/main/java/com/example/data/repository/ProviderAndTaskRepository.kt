package com.example.data.repository

import com.example.data.dao.ActionLogDao
import com.example.data.dao.NoteDao
import com.example.data.dao.PendingApprovalDao
import com.example.data.dao.ProviderDao
import com.example.data.dao.ScheduledTaskDao
import com.example.data.entity.ActionLogEntity
import com.example.data.entity.PendingApprovalEntity
import com.example.data.entity.ProviderEntity
import com.example.data.entity.ScheduledTaskEntity
import com.example.llm.LlmConfig
import com.example.security.SecretDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

class ProviderRepository(
    private val providerDao: ProviderDao,
    private val secretDataStore: SecretDataStore,
) {
    fun observeProviders(): Flow<List<ProviderEntity>> = providerDao.observeAll()

    suspend fun getAllProviders(): List<ProviderEntity> = providerDao.getAll()

    suspend fun getActiveProvider(): ProviderEntity? {
        val activeId = secretDataStore.activeProviderId.first()
        val all = providerDao.getAll()
        return all.firstOrNull { it.id == activeId } ?: all.firstOrNull()
    }

    suspend fun saveProvider(
        id: String = UUID.randomUUID().toString(),
        name: String,
        baseUrl: String,
        defaultModel: String,
        utilityModel: String,
        supportsVision: Boolean,
        contextLimit: Int,
        plainApiKey: String?,
        setAsActive: Boolean = true,
    ): ProviderEntity {
        val entity = ProviderEntity(
            id = id,
            name = name.trim(),
            baseUrl = baseUrl.trim(),
            defaultModel = defaultModel.trim(),
            utilityModel = utilityModel.trim().ifBlank { defaultModel.trim() },
            supportsVision = supportsVision,
            contextLimit = contextLimit.coerceIn(4096, 2000000),
            createdAt = System.currentTimeMillis(),
        )
        providerDao.upsert(entity)
        if (!plainApiKey.isNullOrBlank()) {
            secretDataStore.saveApiKey(entity.id, plainApiKey)
        }
        if (setAsActive) {
            secretDataStore.setActiveProviderId(entity.id)
        }
        return entity
    }

    suspend fun deleteProvider(providerId: String) {
        providerDao.deleteById(providerId)
        secretDataStore.deleteApiKey(providerId)
    }

    suspend fun getApiKey(providerId: String): String = secretDataStore.getApiKey(providerId)

    suspend fun toPrimaryLlmConfig(provider: ProviderEntity): LlmConfig =
        LlmConfig(
            providerId = provider.id,
            baseUrl = provider.baseUrl,
            apiKey = secretDataStore.getApiKey(provider.id),
            model = provider.defaultModel,
            supportsVision = provider.supportsVision,
            contextLimit = provider.contextLimit,
        )

    suspend fun toUtilityLlmConfig(provider: ProviderEntity): LlmConfig =
        LlmConfig(
            providerId = provider.id,
            baseUrl = provider.baseUrl,
            apiKey = secretDataStore.getApiKey(provider.id),
            model = provider.utilityModel.ifBlank { provider.defaultModel },
            supportsVision = false,
            contextLimit = provider.contextLimit,
        )
}

class TaskAndApprovalRepository(
    private val taskDao: ScheduledTaskDao,
    private val approvalDao: PendingApprovalDao,
    private val actionLogDao: ActionLogDao,
    private val noteDao: NoteDao,
) {
    fun observeTasks(): Flow<List<ScheduledTaskEntity>> = taskDao.observeAll()
    fun observeApprovals(): Flow<List<PendingApprovalEntity>> = approvalDao.observeAll()
    fun observePendingApprovals(): Flow<List<PendingApprovalEntity>> = approvalDao.observePending()
    fun observeActionLogs(): Flow<List<ActionLogEntity>> = actionLogDao.observeRecent()
    fun observeNotes() = noteDao.observeAll()
}
