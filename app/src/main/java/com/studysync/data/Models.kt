package com.studysync.data

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val firstName: String = "",
    val lastName: String = "",
    val phoneNumber: String = "",
    val avatarUrl: String = ""
)

data class StudyGroup(
    val id: String,
    val name: String,
    val subject: String,
    val memberCount: Int,
    val lastMessagePreview: String?,
    val createdAt: Long = System.currentTimeMillis(),
    /** Last activity in the group (used for “active groups” and Firestore sync). */
    val lastMessageAt: Long? = null
)

data class Message(
    val id: String,
    val groupId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class Friend(
    val id: String,
    val name: String,
    val email: String,
    val isOnline: Boolean = false,
    val lastInteraction: Long = System.currentTimeMillis()
)

/** Another user added you by email; stored under `users/{yourUid}/friendInbound`. */
data class FriendInbound(
    val id: String,
    val fromUid: String,
    val fromDisplayName: String,
    val fromEmail: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class ClassSession(
    val id: String,
    val title: String,
    val topic: String,
    val startTimeMillis: Long,
    val durationMinutes: Int,
    val location: String,
    val instructor: String,
    val attendeeCount: Int = 0,
    val meetingLink: String = "",
    val roomName: String = "",
    val hostName: String = instructor,
    val isLive: Boolean = false,
    val description: String = "",
    /** Firebase Auth uid of the user who created the class (empty in older / in-memory data). */
    val ownerUid: String = ""
)

data class DashboardSnapshot(
    val nextClass: ClassSession? = null,
    val totalClasses: Int = 0,
    val totalFriends: Int = 0,
    val totalGroups: Int = 0,
    val onlineFriendsCount: Int = 0,
    val activeGroupsCount: Int = 0,
    val recentMessages: List<Message> = emptyList(),
    val spotlightGroups: List<StudyGroup> = emptyList(),
    val recentResources: List<Resource> = emptyList(),
    /** Monotonic server clock for countdowns and “live” UI (emits every second). */
    val nowMillis: Long = System.currentTimeMillis()
)

data class Resource(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val ownerName: String,
    val uploadedAt: Long = System.currentTimeMillis(),
    val groupId: String? = null,
    /** When set, this file is a note attached to an online class (not a group resource). */
    val classId: String? = null,
    val ownerUid: String = ""
)

enum class AuthMode { LOGIN, REGISTER }