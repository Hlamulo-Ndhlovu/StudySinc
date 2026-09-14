package com.studysync.ui.call

/**
 * LiveKit room names must not contain `/` or other path/url characters.
 * Classes may store a full meeting URL in [roomName] or [meetingLink]; navigation and LiveKit
 * both need a single safe segment, stable per class.
 */
object LiveKitRoomNames {
    fun canonicalRoomId(classId: String, roomName: String, meetingLink: String?): String {
        if (classId.isNotBlank()) return "class-$classId"
        return sanitizeFallback(roomName.ifBlank { meetingLink.orEmpty() })
    }

    private fun sanitizeFallback(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return "room-default"
        if (t.contains("://")) {
            val last = t.substringAfterLast('/').substringBefore('?').trim()
            if (last.isNotEmpty()) return last.replace(Regex("[^a-zA-Z0-9_-]"), "-").take(128)
            return "room-${t.hashCode()}"
        }
        return t.replace(Regex("[^a-zA-Z0-9_-]"), "-").take(128)
    }
}
