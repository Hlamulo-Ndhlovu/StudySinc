package com.studysync.notifications

import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Real-time push. Notification messages that arrive while the app is open, and all
 * data messages, are shown here. Background notification messages are drawn by Android
 * on the StudySync channel declared in the manifest.
 */
class StudySyncMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        PushRegistration.persist(uid, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(com.studysync.R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: getString(com.studysync.R.string.notification_push_fallback)
        StudySyncNotifications.showGeneral(this, title, body)
    }
}
