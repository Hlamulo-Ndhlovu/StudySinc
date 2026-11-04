package com.studysync.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_tasks")
data class StudyTaskEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val title: String,
    val subject: String,
    val notes: String,
    val dueAt: Long,
    val priority: Int,
    val done: Boolean,
    val updatedAt: Long,
    val syncState: String,
    val everSynced: Boolean,
    val deleted: Boolean
)
