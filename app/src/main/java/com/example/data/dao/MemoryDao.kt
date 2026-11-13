package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.MemoryEntity
import com.example.data.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE enabled = 1 AND pending_confirmation = 0 ORDER BY updated_at DESC")
    suspend fun getActiveConfirmed(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE category = :category AND enabled = 1")
    suspend fun getByCategory(category: String): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY updated_at DESC")
    suspend fun getAll(): List<MemoryEntity>

    @Query(
        """
        SELECT memories.* FROM memories
        JOIN memories_fts ON (memories.rowid = memories_fts.rowid)
        WHERE memories_fts MATCH :ftsQuery AND memories.enabled = 1 AND memories.pending_confirmation = 0
        ORDER BY memories.updated_at DESC
        LIMIT :limit
        """
    )
    suspend fun searchFts(ftsQuery: String, limit: Int = 12): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memories: List<MemoryEntity>)

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("UPDATE memories SET enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE memories SET pending_confirmation = 0, enabled = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun confirmMemory(id: String, updatedAt: Long)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 'default' LIMIT 1")
    fun observeProfile(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 'default' LIMIT 1")
    suspend fun getProfile(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)
}
