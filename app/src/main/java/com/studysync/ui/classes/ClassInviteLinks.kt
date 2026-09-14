package com.studysync.ui.classes

import android.net.Uri
import com.studysync.data.ClassSession

/**
 * Custom-scheme invite links. The class document id is unguessable; only people with the link can open it in the app.
 * Format: studysync://invite/class/{classId}
 */
object ClassInviteLinks {
    const val SCHEME = "studysync"
    const val HOST = "invite"

    fun buildUri(classId: String): Uri =
        Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendPath("class")
            .appendPath(classId)
            .build()

    fun parseClassId(uri: Uri?): String? {
        if (uri == null) return null
        if (uri.scheme != SCHEME || uri.host != HOST) return null
        val segments = uri.pathSegments
        if (segments.size >= 2 && segments[0] == "class") return segments[1]
        return null
    }

    fun shareMessage(session: ClassSession): String {
        val link = buildUri(session.id).toString()
        return "Join \"${session.title}\" on StudySync\n\n$link"
    }
}
