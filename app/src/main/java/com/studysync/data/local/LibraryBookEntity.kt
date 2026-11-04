package com.studysync.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_books")
data class LibraryBookEntity(
    @PrimaryKey val id: String,
    val bookKey: String,
    val searchQuery: String,
    val title: String,
    val authors: String,
    val year: String,
    val coverUrl: String,
    val cachedAt: Long
)
