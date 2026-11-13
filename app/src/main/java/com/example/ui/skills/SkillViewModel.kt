package com.example.ui.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.skills.SkillItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SkillViewModel(private val container: AppContainer) : ViewModel() {
    val skills: StateFlow<List<SkillItem>> = container.skillRepository.observeAllSkills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveSkill(
        id: String?,
        name: String,
        whenToUse: String,
        steps: String,
        pitfalls: String,
        verification: String,
    ) {
        if (name.isBlank() || steps.isBlank()) return
        viewModelScope.launch {
            val existing = id?.let { container.skillRepository.getSkillById(it) }
            container.skillRepository.saveSkill(
                SkillItem(
                    id = id ?: UUID.randomUUID().toString(),
                    name = name.trim(),
                    whenToUse = whenToUse.trim(),
                    steps = steps.trim(),
                    pitfalls = pitfalls.trim(),
                    verification = verification.trim(),
                    enabled = existing?.enabled ?: true,
                    useCount = existing?.useCount ?: 0,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                )
            )
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { container.skillRepository.setEnabled(id, enabled) }
    }

    fun deleteSkill(id: String) {
        viewModelScope.launch { container.skillRepository.deleteSkill(id) }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SkillViewModel(container) as T
        }
    }
}
