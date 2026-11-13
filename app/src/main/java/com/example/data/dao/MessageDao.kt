package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.AttachmentEntity
import com.example.data.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    fun observeByConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    suspend fun getByConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId AND is_compacted = 0 ORDER BY created_at ASC")
    suspend fun getActiveMessages(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY created_at ASC")
    suspend fun getAll(): List<MessageEntity>

    @Query(
        """
        SELECT messages.* FROM messages
        JOIN messages_fts ON (messages.rowid = messages_fts.rowid)
        WHERE messages_fts MATCH :ftsQuery
        ORDER BY messages.created_at DESC
        LIMIT :limit
        """
    )
    suspend fun searchFts(ftsQuery: String, limit: Int = 25): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("UPDATE messages SET content = :content, prompt_tokens = :promptTokens, completion_tokens = :completionTokens WHERE id = :id")
    suspend fun updateContentAndTokens(id: String, content: String, promptTokens: Int, completionTokens: Int)

    @Query("UPDATE messages SET is_compacted = 1 WHERE id IN (:ids)")
    suspend fun markCompacted(ids: List<String>)

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteByConversation(conversationId: String)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE message_id = :messageId")
    suspend fun getByMessage(messageId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE message_id IN (:messageIds)")
    suspend fun getByMessages(messageIds: List<String>): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AttachmentEntity?

    @Query("SELECT * FROM attachments")
    suspend fun getAll(): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: AttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attachments: List<AttachmentEntity>)
}
