package com.studysync

import android.content.Context
import com.studysync.data.StudyRepository
import com.studysync.data.firebase.FirebaseStudyRepository
import com.studysync.data.local.StudySyncDatabase
import com.studysync.data.remote.LibraryRepository
import com.studysync.data.sync.OfflineStudyRepository

/** Application-wide singletons (Firebase-backed data layer plus on-device Room). */
object Graph {
    val repository: StudyRepository by lazy { FirebaseStudyRepository() }

    /** Theme, language, notifications, and local streak (persisted). */
    val settings: AppSettings by lazy { AppSettings() }

    lateinit var offline: OfflineStudyRepository
        private set

    lateinit var library: LibraryRepository
        private set

    private var attached = false

    fun attach(context: Context) {
        if (attached) return
        val app = context.applicationContext
        settings.attach(app)
        val database = StudySyncDatabase.create(app)
        offline = OfflineStudyRepository(app, database) { settings.notificationsEnabled.value }
        library = LibraryRepository(database)
        offline.start()
        attached = true
    }
}
