package com.studysync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyTaskDao {
    @Query(
        "SELECT * FROM study_tasks WHERE ownerUid = :uid AND deleted = 0 " +
            "ORDER BY done ASC, priority DESC, dueAt ASC"
    )
    fun observeActive(uid: String): Flow<List<StudyTaskEntity>>

    @Query("SELECT * FROM study_tasks WHERE ownerUid = :uid AND syncState != 'SYNCED'")
    suspend fun pending(uid: String): List<StudyTaskEntity>

    @Query("SELECT * FROM study_tasks WHERE ownerUid = :uid AND syncState = 'SYNCED'")
    suspend fun synced(uid: String): List<StudyTaskEntity>

    @Query("SELECT * FROM study_tasks WHERE id = :id LIMIT 1")
    suspend fun get(id: String): StudyTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: StudyTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<StudyTaskEntity>)

    @Query("DELETE FROM study_tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}
