package com.example.files

import com.example.data.AppDatabase
import com.example.data.entity.AttachmentEntity
import com.example.data.entity.ConversationEntity
import com.example.data.entity.MemoryEntity
import com.example.data.entity.MessageEntity
import com.example.data.entity.NoteEntity
import com.example.data.entity.ProfileEntity
import com.example.data.entity.ProviderEntity
import com.example.data.entity.ScheduledTaskEntity
import com.example.data.entity.SkillEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@JsonClass(generateAdapter = true)
data class ArunaBackupBundle(
    val schemaVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val conversations: List<ConversationEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(),
    val memories: List<MemoryEntity> = emptyList(),
    val profile: ProfileEntity? = null,
    val skills: List<SkillEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val scheduledTasks: List<ScheduledTaskEntity> = emptyList(),
    val providers: List<ProviderEntity> = emptyList(), // Never includes API keys
)

class BackupManager(
    private val database: AppDatabase,
    moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
) {
    private val adapter = moshi.adapter(ArunaBackupBundle::class.java).indent("  ")

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val bundle = ArunaBackupBundle(
            conversations = database.conversationDao().getAll(),
            messages = database.messageDao().getAll(),
            attachments = database.attachmentDao().getAll().map { it.copy(base64Preview = null) },
            memories = database.memoryDao().getAll(),
            profile = database.profileDao().getProfile(),
            skills = database.skillDao().getAll(),
            notes = database.noteDao().getAll(),
            scheduledTasks = database.scheduledTaskDao().getAll(),
            providers = database.providerDao().getAll(),
        )
        adapter.toJson(bundle)
    }

    suspend fun exportNotesMarkdown(): String = withContext(Dispatchers.IO) {
        val notes = database.noteDao().getAll()
        if (notes.isEmpty()) return@withContext "# Catatan Aruna\n\n(Belum ada catatan tersimpan.)\n"
        buildString {
            appendLine("# Catatan Aruna")
            appendLine()
            notes.forEach { note ->
                appendLine("## ${note.title}")
                appendLine()
                appendLine(note.body)
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }

    suspend fun importJson(jsonString: String): Int = withContext(Dispatchers.IO) {
        val bundle = adapter.fromJson(jsonString)
            ?: throw IllegalArgumentException("Format berkas backup JSON tidak valid")

        bundle.providers.forEach { database.providerDao().upsert(it) }
        bundle.conversations.forEach { database.conversationDao().upsert(it) }
        if (bundle.messages.isNotEmpty()) database.messageDao().insertAll(bundle.messages)
        if (bundle.attachments.isNotEmpty()) database.attachmentDao().insertAll(bundle.attachments)
        if (bundle.memories.isNotEmpty()) database.memoryDao().insertAll(bundle.memories)
        bundle.profile?.let { database.profileDao().upsert(it) }
        if (bundle.skills.isNotEmpty()) database.skillDao().insertAll(bundle.skills)
        if (bundle.notes.isNotEmpty()) database.noteDao().insertAll(bundle.notes)
        if (bundle.scheduledTasks.isNotEmpty()) database.scheduledTaskDao().insertAll(bundle.scheduledTasks)

        bundle.conversations.size + bundle.memories.size + bundle.skills.size + bundle.notes.size
    }
}
