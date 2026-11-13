package com.example.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.agent.model.AgentEvent
import com.example.agent.model.ExecutionMode
import com.example.data.entity.AttachmentEntity
import com.example.data.entity.ConversationEntity
import com.example.data.entity.MessageEntity
import com.example.data.entity.ProviderEntity
import com.example.skills.SkillItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class PendingAttachment(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isImage: Boolean,
    val base64DataUrl: String? = null,
    val extractedText: String? = null,
)

data class LiveToolBanner(
    val toolName: String,
    val summary: String,
    val isRunning: Boolean,
    val success: Boolean? = null,
)

data class ChatUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val currentConversation: ConversationEntity? = null,
    val messages: List<MessageEntity> = emptyList(),
    val attachmentsByMessage: Map<String, List<AttachmentEntity>> = emptyMap(),
    val activeProvider: ProviderEntity? = null,
    val streamingText: String = "",
    val isGenerating: Boolean = false,
    val isCompacting: Boolean = false,
    val liveToolBanner: LiveToolBanner? = null,
    val pendingAttachments: List<PendingAttachment> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<MessageEntity> = emptyList(),
    val statusNotice: String? = null,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val selectedConversationId = MutableStateFlow<String?>(null)
    private val streamingText = MutableStateFlow("")
    private val isGenerating = MutableStateFlow(false)
    private val isCompacting = MutableStateFlow(false)
    private val liveToolBanner = MutableStateFlow<LiveToolBanner?>(null)
    private val pendingAttachments = MutableStateFlow<List<PendingAttachment>>(emptyList())
    private val searchQuery = MutableStateFlow("")
    private val searchResults = MutableStateFlow<List<MessageEntity>>(emptyList())
    private val attachmentsMap = MutableStateFlow<Map<String, List<AttachmentEntity>>>(emptyMap())
    private val statusNotice = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val conversationsFlow = container.chatRepository.observeConversations()
    private val providersFlow = container.providerRepository.observeProviders()

    private val activeMessagesFlow = selectedConversationId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else container.chatRepository.observeMessages(id)
    }

    private val activeConversationFlow = selectedConversationId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else container.chatRepository.observeConversation(id)
    }

    private val coreDataFlow = combine(
        conversationsFlow,
        activeConversationFlow,
        activeMessagesFlow,
        attachmentsMap,
        providersFlow,
    ) { convs, currentConv, msgs, attMap, providers ->
        Quint(convs, currentConv, msgs, attMap, providers)
    }

    private val liveStateFlow = combine(
        streamingText,
        isGenerating,
        isCompacting,
        liveToolBanner,
    ) { s, g, c, t ->
        Quad(s, g, c, t)
    }

    private val extrasStateFlow = combine(
        pendingAttachments,
        searchQuery,
        searchResults,
        statusNotice,
        errorMessage,
    ) { pa, sq, sr, sn, em ->
        Quint(pa, sq, sr, sn, em)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        coreDataFlow,
        liveStateFlow,
        extrasStateFlow,
        container.secretDataStore.activeProviderId,
    ) { core, live, extras, activeProvId ->
        val activeProvider = core.fifth.firstOrNull { it.id == activeProvId } ?: core.fifth.firstOrNull()
        ChatUiState(
            conversations = core.first,
            currentConversation = core.second,
            messages = core.third,
            attachmentsByMessage = core.fourth,
            activeProvider = activeProvider,
            streamingText = live.first,
            isGenerating = live.second,
            isCompacting = live.third,
            liveToolBanner = live.fourth,
            pendingAttachments = extras.first,
            searchQuery = extras.second,
            searchResults = extras.third,
            statusNotice = extras.fourth,
            errorMessage = extras.fifth,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState(),
    )

    init {
        viewModelScope.launch {
            val savedId = container.secretDataStore.activeConversationId.first()
            val all = conversationsFlow.first()
            val target = all.firstOrNull { it.id == savedId } ?: all.firstOrNull()
            if (target != null) {
                selectedConversationId.value = target.id
            }
        }
        viewModelScope.launch {
            activeMessagesFlow.collect { msgs ->
                if (msgs.isNotEmpty()) {
                    attachmentsMap.value = container.chatRepository.getAttachmentsForMessages(msgs.map { it.id })
                } else {
                    attachmentsMap.value = emptyMap()
                }
            }
        }
    }

    fun selectConversation(conversationId: String) {
        selectedConversationId.value = conversationId
        viewModelScope.launch {
            container.secretDataStore.setActiveConversationId(conversationId)
        }
    }

    fun createNewChat(carryLastSummary: Boolean) {
        viewModelScope.launch {
            val provider = container.providerRepository.getActiveProvider()
            val conv = container.chatRepository.createConversation(
                title = "Obrolan Baru",
                providerId = provider?.id ?: "default",
                model = provider?.defaultModel ?: "gpt-4o-mini",
                carrySummaryFromLastSession = carryLastSummary,
            )
            selectedConversationId.value = conv.id
            container.secretDataStore.setActiveConversationId(conv.id)
            if (carryLastSummary && !conv.summary.isNullOrBlank()) {
                statusNotice.value = "Obrolan baru dimulai dengan membawa ringkasan sesi terakhir."
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            container.chatRepository.deleteConversation(conversationId)
            if (selectedConversationId.value == conversationId) {
                val next = conversationsFlow.first().firstOrNull { it.id != conversationId }
                selectedConversationId.value = next?.id
                container.secretDataStore.setActiveConversationId(next?.id)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
        viewModelScope.launch {
            searchResults.value = if (query.isBlank()) {
                emptyList()
            } else {
                container.chatRepository.searchMessagesFts(query, limit = 15)
            }
        }
    }

    fun attachPhoto(uri: Uri) {
        viewModelScope.launch {
            val compressed = container.imageCompressor.compressUri(uri)
            if (compressed == null) {
                errorMessage.value = "Gagal memproses foto yang dipilih."
                return@launch
            }
            pendingAttachments.value = pendingAttachments.value + PendingAttachment(
                uriString = uri.toString(),
                fileName = compressed.fileName,
                mimeType = compressed.mimeType,
                sizeBytes = compressed.sizeBytes,
                isImage = true,
                base64DataUrl = compressed.dataUrl,
            )
        }
    }

    fun attachDocument(uri: Uri) {
        viewModelScope.launch {
            runCatching { container.documentTextExtractor.extractFromUri(uri) }
                .onSuccess { doc ->
                    pendingAttachments.value = pendingAttachments.value + PendingAttachment(
                        uriString = uri.toString(),
                        fileName = doc.fileName,
                        mimeType = doc.mimeType,
                        sizeBytes = doc.sizeBytes,
                        isImage = false,
                        extractedText = doc.extractedText,
                    )
                }
                .onFailure { ex ->
                    errorMessage.value = "Gagal membaca dokumen: ${ex.message}"
                }
        }
    }

    fun removePendingAttachment(id: String) {
        pendingAttachments.value = pendingAttachments.value.filterNot { it.id == id }
    }

    fun sendMessage(rawText: String) {
        val text = rawText.trim()
        if (text == "/compact") {
            triggerManualCompact()
            return
        }
        val attachmentsToSend = pendingAttachments.value
        if (text.isEmpty() && attachmentsToSend.isEmpty()) return
        if (isGenerating.value) return

        viewModelScope.launch {
            val provider = container.providerRepository.getActiveProvider()
            if (provider == null) {
                errorMessage.value = "Silakan tambahkan provider & API key terlebih dahulu di menu Setelan."
                return@launch
            }
            val apiKey = container.providerRepository.getApiKey(provider.id)
            if (apiKey.isBlank() && !provider.baseUrl.contains("localhost") && !provider.baseUrl.contains("127.0.0.1") && !provider.baseUrl.contains("10.0.2.2")) {
                errorMessage.value = "API Key untuk ${provider.name} masih kosong. Isi di menu Setelan."
                return@launch
            }

            var convId = selectedConversationId.value
            if (convId == null) {
                val created = container.chatRepository.createConversation(
                    title = text.take(40).ifBlank { "Lampiran" },
                    providerId = provider.id,
                    model = provider.defaultModel,
                )
                convId = created.id
                selectedConversationId.value = convId
                container.secretDataStore.setActiveConversationId(convId)
            }

            val attachmentEntities = attachmentsToSend.map { pa ->
                AttachmentEntity(
                    id = pa.id,
                    messageId = "",
                    uri = pa.uriString,
                    fileName = pa.fileName,
                    mimeType = pa.mimeType,
                    sizeBytes = pa.sizeBytes,
                    extractedText = pa.extractedText,
                    isImage = pa.isImage,
                    base64Preview = pa.base64DataUrl,
                )
            }

            pendingAttachments.value = emptyList()
            container.chatRepository.saveUserMessageWithAttachments(
                conversationId = convId,
                content = text,
                attachments = attachmentEntities,
            )

            val primaryConfig = container.providerRepository.toPrimaryLlmConfig(provider)
            val utilityConfig = container.providerRepository.toUtilityLlmConfig(provider)

            isGenerating.value = true
            streamingText.value = ""
            errorMessage.value = null
            liveToolBanner.value = null

            container.agentLoop.run(
                conversationId = convId,
                primaryConfig = primaryConfig,
                utilityConfig = utilityConfig,
                latestUserQuery = text,
                mode = ExecutionMode.FOREGROUND,
            ).collect { event ->
                when (event) {
                    is AgentEvent.StreamingDelta -> {
                        streamingText.value = event.fullTextSoFar
                    }
                    is AgentEvent.ToolExecuting -> {
                        liveToolBanner.value = LiveToolBanner(
                            toolName = event.toolName,
                            summary = event.argumentsSummary,
                            isRunning = true,
                        )
                    }
                    is AgentEvent.ToolCompleted -> {
                        liveToolBanner.value = LiveToolBanner(
                            toolName = event.toolName,
                            summary = event.resultPreview,
                            isRunning = false,
                            success = event.success,
                        )
                    }
                    is AgentEvent.WaitingApproval -> Unit
                    AgentEvent.CompactingContext -> {
                        isCompacting.value = true
                        statusNotice.value = "Merangkum konteks otomatis (70% batas token)…"
                    }
                    is AgentEvent.MemorySynced -> {
                        statusNotice.value = "${event.changesCount} fakta memori diperbarui."
                    }
                    is AgentEvent.Finished -> {
                        isGenerating.value = false
                        isCompacting.value = false
                        streamingText.value = ""
                        liveToolBanner.value = null
                    }
                    is AgentEvent.Error -> {
                        isGenerating.value = false
                        isCompacting.value = false
                        streamingText.value = ""
                        liveToolBanner.value = null
                        errorMessage.value = event.message
                    }
                }
            }
        }
    }

    fun triggerManualCompact() {
        val convId = selectedConversationId.value ?: return
        if (isGenerating.value || isCompacting.value) return
        viewModelScope.launch {
            val provider = container.providerRepository.getActiveProvider() ?: return@launch
            val utilityConfig = container.providerRepository.toUtilityLlmConfig(provider)
            isCompacting.value = true
            val compacted = runCatching {
                container.compactor.compact(convId, utilityConfig)
            }.getOrDefault(false)
            isCompacting.value = false
            statusNotice.value = if (compacted) {
                "Percakapan berhasil di-compact; 6 pesan terakhir tetap utuh."
            } else {
                "Belum cukup pesan aktif untuk di-compact (minimal > 6 pesan)."
            }
        }
    }

    fun saveAsSkill(name: String, whenToUse: String, steps: String, pitfalls: String, verification: String) {
        viewModelScope.launch {
            container.skillRepository.saveSkill(
                SkillItem(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    whenToUse = whenToUse.trim(),
                    steps = steps.trim(),
                    pitfalls = pitfalls.trim(),
                    verification = verification.trim(),
                    enabled = true,
                )
            )
            statusNotice.value = "Skill '$name' berhasil disimpan ke katalog."
        }
    }

    fun clearNotices() {
        statusNotice.value = null
        errorMessage.value = null
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(container) as T
        }
    }
}
