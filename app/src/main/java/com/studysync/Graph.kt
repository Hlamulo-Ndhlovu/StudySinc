package com.studysync

import com.studysync.data.StudyRepository
import com.studysync.data.firebase.FirebaseStudyRepository

/** Application-wide singletons (Firebase-backed data layer). */
object Graph {
    val repository: StudyRepository by lazy { FirebaseStudyRepository() }

    /** Theme, language, notifications, and local streak (persisted). */
    val settings: AppSettings by lazy { AppSettings() }
}
