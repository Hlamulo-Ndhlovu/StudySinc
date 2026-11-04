package com.studysync.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [StudyTaskEntity::class, LibraryBookEntity::class],
    version = 1,
    exportSchema = false
)
abstract class StudySyncDatabase : RoomDatabase() {
    abstract fun studyTaskDao(): StudyTaskDao
    abstract fun libraryBookDao(): LibraryBookDao

    companion object {
        fun create(context: Context): StudySyncDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                StudySyncDatabase::class.java,
                "studysync.db"
            ).fallbackToDestructiveMigration().build()
    }
}
