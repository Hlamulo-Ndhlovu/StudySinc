package com.studysync.notifications

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.tasks.await

/** Saves the device FCM token on the signed-in user and listens for study updates. */
object PushRegistration {

    const val TOPIC_UPDATES = "studysync_updates"

    suspend fun saveCurrentToken() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        val token = Firebase.messaging.token.await()
        persist(uid, token)
    }

    fun persist(uid: String, token: String) {
        if (token.isBlank()) return
        Firebase.firestore.collection("users").document(uid)
            .set(
                mapOf(
                    "fcmToken" to token,
                    "fcmUpdatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
    }

    fun subscribeToStudyUpdates() {
        Firebase.messaging.subscribeToTopic(TOPIC_UPDATES)
    }
}
