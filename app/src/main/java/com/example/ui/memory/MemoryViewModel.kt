package com.example.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.memory.MemoryItem
import com.example.memory.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class MemoryUiState(
    val memories: List<MemoryItem> = emptyList(),
    val profile: UserProfile? = null,
    val selectedCategory: String = "Semua",
    val searchQuery: String = "",
    val categories: List<String> = listOf("Semua"),
)

class MemoryViewModel(private val container: AppContainer) : ViewModel() {
    private val selectedCategory = MutableStateFlow("Semua")
    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<MemoryUiState> = combine(
        container.memoryRepository.observeMemories(),
        container.memoryRepository.observeProfile(),
        selectedCategory,
        searchQuery,
    ) { memories, profile, cat, query ->
        val cats = listOf("Semua") + memories.map { it.category }.distinct().sorted()
        val filtered = memories.filter { m ->
            val catOk = cat == "Semua" || m.category.equals(cat, ignoreCase = true)
            val queryOk = query.isBlank() ||
                m.text.contains(query, ignoreCase = true) ||
                m.tags.contains(query, ignoreCase = true)
            catOk && queryOk
        }
        MemoryUiState(
            memories = filtered,
            profile = profile,
            selectedCategory = cat,
            searchQuery = query,
            categories = cats,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MemoryUiState())

    fun selectCategory(category: String) {
        selectedCategory.value = category
    }

    fun updateSearch(query: String) {
        searchQuery.value = query
    }

    fun addOrUpdateMemory(id: String?, text: String, category: String, tags: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            if (id == null) {
                container.memoryRepository.addMemory(
                    MemoryItem(
                        id = UUID.randomUUID().toString(),
                        text = text.trim(),
                        category = category.trim().ifBlank { "preferensi" },
                        tags = tags.trim(),
                        enabled = true,
                        pendingConfirmation = false,
                    )
                )
            } else {
                container.memoryRepository.updateMemory(id, text.trim(), category.trim(), tags.trim())
            }
        }
    }

    fun toggleMemory(id: String, enabled: Boolean) {
        viewModelScope.launch { container.memoryRepository.setMemoryEnabled(id, enabled) }
    }

    fun confirmSensitiveMemory(id: String) {
        viewModelScope.launch { container.memoryRepository.confirmSensitiveMemory(id) }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch { container.memoryRepository.deleteMemory(id) }
    }

    fun saveProfile(summaryText: String) {
        viewModelScope.launch { container.memoryRepository.saveProfile(summaryText.trim()) }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MemoryViewModel(container) as T
        }
    }
}
