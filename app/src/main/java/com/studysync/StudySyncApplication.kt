package com.studysync

import android.app.Application
import com.studysync.notifications.PushRegistration
import com.studysync.notifications.StudySyncNotifications

class StudySyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.attach(this)
        StudySyncNotifications.ensureChannels(this)
        runCatching { PushRegistration.subscribeToStudyUpdates() }
    }
}
