package com.example.data.repository

import com.example.agent.AgentConversationStore
import com.example.agent.CompactionPersistence
import com.example.agent.model.AgentStoredMessage
import com.example.data.FtsQueryHelper
import com.example.data.dao.AttachmentDao
import com.example.data.dao.ConversationDao
import com.example.data.dao.MessageDao
import com.example.data.entity.AttachmentEntity
import com.example.data.entity.ConversationEntity
import com.example.data.entity.MessageEntity
import com.example.files.TextChunker
import com.example.llm.LlmToolCall
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ChatRepositoryImpl(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val attachmentDao: AttachmentDao,
    moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
) : AgentConversationStore, CompactionPersistence {

    private val toolCallsType = Types.newParameterizedType(List::class.java, LlmToolCall::class.java)
    private val toolCallsAdapter = moshi.adapter<List<LlmToolCall>>(toolCallsType)

    fun observeConversations(): Flow<List<ConversationEntity>> = conversationDao.observeAll()

    fun observeConversation(id: String): Flow<ConversationEntity?> = conversationDao.observeById(id)

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.observeByConversation(conversationId)

    suspend fun getAttachmentsForMessages(messageIds: List<String>): Map<String, List<AttachmentEntity>> {
        if (messageIds.isEmpty()) return emptyMap()
        return attachmentDao.getByMessages(messageIds).groupBy { it.messageId }
    }

    suspend fun createConversation(
        title: String,
        providerId: String,
        model: String,
        carrySummaryFromLastSession: Boolean = false,
    ): ConversationEntity {
        val now = System.currentTimeMillis()
        val carriedSummary = if (carrySummaryFromLastSession) {
            val latest = conversationDao.getLatest()
            latest?.summary?.takeIf { it.isNotBlank() } ?: latest?.let { prev ->
                val msgs = messageDao.getByConversation(prev.id).takeLast(4)
                if (msgs.isEmpty()) null
                else "Konteks dari sesi sebelumnya (${prev.title}): " +
                    msgs.joinToString(" | ") { "${it.role}: ${it.content.take(120)}" }
            }
        } else null

        val entity = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            providerId = providerId,
            model = model,
            summary = carriedSummary,
            totalTokens = 0,
            createdAt = now,
            updatedAt = now,
        )
        conversationDao.upsert(entity)
        return entity
    }

    suspend fun deleteConversation(conversationId: String) {
        messageDao.deleteByConversation(conversationId)
        conversationDao.deleteById(conversationId)
    }

    suspend fun searchMessagesFts(query: String, limit: Int = 20): List<MessageEntity> {
        val fts = FtsQueryHelper.toMatchQuery(query)
        if (fts.isBlank()) return emptyList()
        return runCatching { messageDao.searchFts(fts, limit) }.getOrDefault(emptyList())
    }

    suspend fun saveUserMessageWithAttachments(
        conversationId: String,
        content: String,
        attachments: List<AttachmentEntity>,
    ): MessageEntity {
        val now = System.currentTimeMillis()
        val msgId = UUID.randomUUID().toString()
        val message = MessageEntity(
            id = msgId,
            conversationId = conversationId,
            role = "user",
            content = content,
            createdAt = now,
        )
        messageDao.insert(message)
        if (attachments.isNotEmpty()) {
            attachmentDao.insertAll(attachments.map { it.copy(messageId = msgId) })
        }

        // Auto-title conversation on first user message
        val conv = conversationDao.getById(conversationId)
        if (conv != null && (conv.title == "Obrolan Baru" || conv.title.isBlank())) {
            val autoTitle = content.trim().replace('\n', ' ').take(42).ifBlank { "Lampiran" }
            conversationDao.updateTitle(conversationId, autoTitle, now)
        }
        return message
    }

    override suspend fun getConversationSummary(conversationId: String): String? =
        conversationDao.getById(conversationId)?.summary

    override suspend fun getActiveMessages(conversationId: String): List<AgentStoredMessage> {
        val active = messageDao.getActiveMessages(conversationId)
        if (active.isEmpty()) return emptyList()
        val attachmentsByMsg = attachmentDao.getByMessages(active.map { it.id }).groupBy { it.messageId }

        return active.map { msg ->
            val msgAttachments = attachmentsByMsg[msg.id].orEmpty()
            val images = msgAttachments.mapNotNull { att ->
                if (att.isImage) att.base64Preview else null
            }
            val docNotes = msgAttachments
                .filter { !it.isImage }
                .mapNotNull { att ->
                    val extracted = att.extractedText.orEmpty()
                    if (extracted.length <= 2400) {
                        "[Lampiran dokumen '${att.fileName}']:\n$extracted"
                    } else {
                        val totalChunks = TextChunker.chunk(extracted).size
                        "[Lampiran dokumen besar '${att.fileName}' (attachment_id=${att.id}, total_chunks=$totalChunks, ${extracted.length} karakter). Gunakan tool `baca_berkas` dengan attachment_id='${att.id}' untuk membaca bagiannya.]"
                    }
                }
                .joinToString("\n\n")
                .takeIf { it.isNotBlank() }

            val toolCalls = msg.toolCallsJson?.let { raw ->
                runCatching { toolCallsAdapter.fromJson(raw) }.getOrNull()
            }.orEmpty()

            AgentStoredMessage(
                id = msg.id,
                role = msg.role,
                content = msg.content,
                toolCalls = toolCalls,
                toolCallId = msg.toolCallId,
                toolName = msg.toolName,
                imageDataUrls = images,
                documentContextNote = docNotes,
                promptTokens = msg.promptTokens,
                completionTokens = msg.completionTokens,
                isCompacted = msg.isCompacted,
                createdAt = msg.createdAt,
            )
        }
    }

    override suspend fun getTurnCountSinceMemory(conversationId: String): Int =
        conversationDao.getById(conversationId)?.turnCountSinceMemory ?: 0

    override suspend fun setTurnCountSinceMemory(conversationId: String, count: Int) {
        conversationDao.updateTurnCount(conversationId, count)
    }

    override suspend fun appendMessage(conversationId: String, message: AgentStoredMessage) {
        val toolJson = if (message.toolCalls.isNotEmpty()) {
            toolCallsAdapter.toJson(message.toolCalls)
        } else null
        messageDao.insert(
            MessageEntity(
                id = message.id,
                conversationId = conversationId,
                role = message.role,
                content = message.content,
                toolCallsJson = toolJson,
                toolCallId = message.toolCallId,
                toolName = message.toolName,
                promptTokens = message.promptTokens,
                completionTokens = message.completionTokens,
                isCompacted = message.isCompacted,
                createdAt = message.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            )
        )
    }

    override suspend fun updateConversationTokens(conversationId: String, totalTokens: Int) {
        conversationDao.updateTotalTokens(conversationId, totalTokens, System.currentTimeMillis())
    }

    override suspend fun saveCompactionResult(
        conversationId: String,
        newSummary: String,
        compactedMessageIds: List<String>,
        estimatedRemainingTokens: Int,
    ) {
        val now = System.currentTimeMillis()
        conversationDao.updateSummary(conversationId, newSummary, now)
        if (compactedMessageIds.isNotEmpty()) {
            messageDao.markCompacted(compactedMessageIds)
        }
        conversationDao.updateTotalTokens(conversationId, estimatedRemainingTokens, now)
    }
}
