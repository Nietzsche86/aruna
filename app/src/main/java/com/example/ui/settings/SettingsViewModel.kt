package com.example.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.entity.ProviderEntity
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class SettingsUiState(
    val providers: List<ProviderEntity> = emptyList(),
    val activeProviderId: String? = null,
    val dynamicColorEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val discoveredModels: List<String> = emptyList(),
    val isFetchingModels: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val discoveredModels = MutableStateFlow<List<String>>(emptyList())
    private val isFetchingModels = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        container.providerRepository.observeProviders(),
        container.secretDataStore.activeProviderId,
        container.secretDataStore.dynamicColorEnabled,
        container.secretDataStore.themeMode,
        combine(discoveredModels, isFetchingModels, statusMessage, errorMessage) { dm, fm, sm, em ->
            Quad(dm, fm, sm, em)
        },
    ) { providers, activeId, dynamicColor, themeMode, extras ->
        SettingsUiState(
            providers = providers,
            activeProviderId = activeId ?: providers.firstOrNull()?.id,
            dynamicColorEnabled = dynamicColor,
            themeMode = themeMode,
            discoveredModels = extras.first,
            isFetchingModels = extras.second,
            statusMessage = extras.third,
            errorMessage = extras.fourth,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun saveProvider(
        existingId: String?,
        name: String,
        baseUrl: String,
        apiKey: String,
        defaultModel: String,
        utilityModel: String,
        supportsVision: Boolean,
        contextLimit: Int,
    ) {
        if (name.isBlank() || baseUrl.isBlank() || defaultModel.isBlank()) {
            errorMessage.value = "Nama provider, Base URL, dan Model Utama wajib diisi."
            return
        }
        viewModelScope.launch {
            container.providerRepository.saveProvider(
                id = existingId ?: UUID.randomUUID().toString(),
                name = name,
                baseUrl = baseUrl,
                defaultModel = defaultModel,
                utilityModel = utilityModel,
                supportsVision = supportsVision,
                contextLimit = contextLimit,
                plainApiKey = apiKey.takeIf { it.isNotBlank() },
                setAsActive = true,
            )
            statusMessage.value = "Provider '$name' disimpan dan diaktifkan."
            errorMessage.value = null
        }
    }

    fun selectActiveProvider(providerId: String) {
        viewModelScope.launch {
            container.secretDataStore.setActiveProviderId(providerId)
            statusMessage.value = "Provider aktif berhasil diganti."
        }
    }

    fun deleteProvider(providerId: String) {
        viewModelScope.launch {
            container.providerRepository.deleteProvider(providerId)
            statusMessage.value = "Provider dihapus."
        }
    }

    fun fetchModels(baseUrl: String, apiKeyInput: String, existingProviderId: String?) {
        if (baseUrl.isBlank()) {
            errorMessage.value = "Masukkan Base URL terlebih dahulu."
            return
        }
        viewModelScope.launch {
            isFetchingModels.value = true
            errorMessage.value = null
            val key = apiKeyInput.ifBlank {
                existingProviderId?.let { container.providerRepository.getApiKey(it) }.orEmpty()
            }
            runCatching {
                container.llmProvider.listModels(baseUrl, key)
            }.onSuccess { models ->
                discoveredModels.value = models
                statusMessage.value = if (models.isNotEmpty()) {
                    "${models.size} model ditemukan dari endpoint."
                } else {
                    "Endpoint tidak mengembalikan daftar model; ketik nama model manual."
                }
            }.onFailure { ex ->
                errorMessage.value = ex.message ?: "Gagal mengambil daftar model."
            }
            isFetchingModels.value = false
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { container.secretDataStore.setDynamicColor(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.secretDataStore.setThemeMode(mode) }
    }

    fun exportBackupToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = container.backupManager.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
            }.onSuccess {
                statusMessage.value = "Backup JSON berhasil diekspor (tanpa API key)."
            }.onFailure { ex ->
                errorMessage.value = "Gagal mengekspor backup: ${ex.message}"
            }
        }
    }

    fun exportNotesMarkdownToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val md = container.backupManager.exportNotesMarkdown()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(md.toByteArray(Charsets.UTF_8))
                    }
                }
            }.onSuccess {
                statusMessage.value = "Catatan berhasil diekspor ke Markdown (.md)."
            }.onFailure { ex ->
                errorMessage.value = "Gagal mengekspor catatan: ${ex.message}"
            }
        }
    }

    fun importBackupFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    }.orEmpty()
                }
                container.backupManager.importJson(json)
            }.onSuccess { count ->
                statusMessage.value = "Backup dipulihkan ($count entitas diproses)."
            }.onFailure { ex ->
                errorMessage.value = "Gagal mengimpor backup: ${ex.message}"
            }
        }
    }

    fun clearNotices() {
        statusMessage.value = null
        errorMessage.value = null
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(container) as T
        }
    }
}
