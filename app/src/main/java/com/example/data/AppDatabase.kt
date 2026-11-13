package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.ActionLogDao
import com.example.data.dao.AttachmentDao
import com.example.data.dao.ConversationDao
import com.example.data.dao.MemoryDao
import com.example.data.dao.MessageDao
import com.example.data.dao.NoteDao
import com.example.data.dao.PendingApprovalDao
import com.example.data.dao.ProfileDao
import com.example.data.dao.ProviderDao
import com.example.data.dao.ScheduledTaskDao
import com.example.data.dao.SkillDao
import com.example.data.entity.ActionLogEntity
import com.example.data.entity.AttachmentEntity
import com.example.data.entity.ConversationEntity
import com.example.data.entity.MemoryEntity
import com.example.data.entity.MemoryFtsEntity
import com.example.data.entity.MessageEntity
import com.example.data.entity.MessageFtsEntity
import com.example.data.entity.NoteEntity
import com.example.data.entity.NoteFtsEntity
import com.example.data.entity.PendingApprovalEntity
import com.example.data.entity.ProfileEntity
import com.example.data.entity.ProviderEntity
import com.example.data.entity.ScheduledTaskEntity
import com.example.data.entity.SkillEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MessageFtsEntity::class,
        AttachmentEntity::class,
        MemoryEntity::class,
        MemoryFtsEntity::class,
        ProfileEntity::class,
        SkillEntity::class,
        NoteEntity::class,
        NoteFtsEntity::class,
        ScheduledTaskEntity::class,
        PendingApprovalEntity::class,
        ProviderEntity::class,
        ActionLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun memoryDao(): MemoryDao
    abstract fun profileDao(): ProfileDao
    abstract fun skillDao(): SkillDao
    abstract fun noteDao(): NoteDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun pendingApprovalDao(): PendingApprovalDao
    abstract fun providerDao(): ProviderDao
    abstract fun actionLogDao(): ActionLogDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "aruna_local.db",
            ).build()
    }
}
