package com.example.data.repository

import com.example.data.dao.SkillDao
import com.example.data.entity.SkillEntity
import com.example.skills.SkillItem
import com.example.skills.SkillRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SkillRepositoryImpl(
    private val skillDao: SkillDao,
) : SkillRepository {

    fun observeAllSkills(): Flow<List<SkillItem>> =
        skillDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getEnabledSkills(): List<SkillItem> =
        skillDao.getEnabled().map { it.toDomain() }

    override suspend fun getSkillById(id: String): SkillItem? =
        skillDao.getById(id)?.toDomain()

    override suspend fun incrementUseCount(id: String) {
        skillDao.incrementUseCount(id)
    }

    suspend fun saveSkill(item: SkillItem) {
        skillDao.upsert(
            SkillEntity(
                id = item.id,
                name = item.name,
                whenToUse = item.whenToUse,
                steps = item.steps,
                pitfalls = item.pitfalls,
                verification = item.verification,
                enabled = item.enabled,
                useCount = item.useCount,
                createdAt = item.createdAt,
            )
        )
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        skillDao.setEnabled(id, enabled)
    }

    suspend fun deleteSkill(id: String) {
        skillDao.deleteById(id)
    }

    private fun SkillEntity.toDomain() = SkillItem(
        id = id,
        name = name,
        whenToUse = whenToUse,
        steps = steps,
        pitfalls = pitfalls,
        verification = verification,
        enabled = enabled,
        useCount = useCount,
        createdAt = createdAt,
    )
}
