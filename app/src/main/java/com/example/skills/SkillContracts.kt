package com.example.skills

data class SkillItem(
    val id: String,
    val name: String,
    val whenToUse: String,
    val steps: String,
    val pitfalls: String = "",
    val verification: String = "",
    val enabled: Boolean = true,
    val useCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

interface SkillRepository {
    suspend fun getEnabledSkills(): List<SkillItem>
    suspend fun getSkillById(id: String): SkillItem?
    suspend fun incrementUseCount(id: String)
}

class SkillCatalogBuilder {
    /**
     * Builds Stage-1 lightweight skill header catalog (id, name, when_to_use)
     * for cache-friendly static system prompt injection.
     */
    fun buildHeaderCatalog(skills: List<SkillItem>): String {
        val active = skills.filter { it.enabled }
        if (active.isEmpty()) return ""
        return buildString {
            appendLine("## Katalog Skill Tersedia (Panggil tool `baca_skill` dengan `id` jika relevan)")
            active.forEach { skill ->
                appendLine("- id: `${skill.id}` | nama: ${skill.name} | kapan dipakai: ${skill.whenToUse}")
            }
        }.trim()
    }
}
