package com.studysync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LibraryBookDao {
    @Query("SELECT * FROM library_books WHERE searchQuery = :query ORDER BY title COLLATE NOCASE")
    suspend fun forQuery(query: String): List<LibraryBookEntity>

    @Query("SELECT * FROM library_books ORDER BY cachedAt DESC LIMIT 20")
    suspend fun recent(): List<LibraryBookEntity>

    @Query("DELETE FROM library_books WHERE searchQuery = :query")
    suspend fun deleteQuery(query: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<LibraryBookEntity>)
}
