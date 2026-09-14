package com.studysync.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface StudyRepository {
    val currentUser: StateFlow<User?>

    suspend fun login(email: String, password: String): Result<User>

    /** Sends a password reset email (Firebase Auth). */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    suspend fun register(
        email: String,
        password: String,
        displayName: String
    ): Result<User>

    /** Sign in with Google (Firebase Auth + Google ID token). */
    suspend fun signInWithGoogle(idToken: String): Result<User>

    fun observeGroups(): Flow<List<StudyGroup>>

    fun observeGroup(groupId: String): Flow<StudyGroup?>

    suspend fun createGroup(name: String, subject: String): Result<StudyGroup>

    fun observeMessages(groupId: String): Flow<List<Message>>

    /** Live typing line for the group (e.g. "Alex is typing…"). */
    fun observeTyping(groupId: String): Flow<String?>

    suspend fun reportTyping(groupId: String, isTyping: Boolean)

    suspend fun sendMessage(groupId: String, content: String): Result<Unit>

    fun observeFriends(): Flow<List<Friend>>

    suspend fun addFriend(name: String, email: String): Result<Friend>

    /** People who added the current user by matching email (Firestore). */
    fun observeFriendInbound(): Flow<List<FriendInbound>>

    fun observeClasses(): Flow<List<ClassSession>>

    suspend fun createClass(
        title: String,
        topic: String,
        startTimeMillis: Long,
        durationMinutes: Int,
        location: String,
        instructor: String,
        meetingLink: String,
        isLive: Boolean,
        description: String
    ): Result<ClassSession>

    fun observeClass(classId: String): Flow<ClassSession?>

    suspend fun updateClassLive(classId: String, isLive: Boolean): Result<Unit>

    /** Deletes the class and its notes (creator only; enforced in repository + Firestore rules). */
    suspend fun deleteClass(classId: String): Result<Unit>

    fun observeClassNotes(classId: String): Flow<List<Resource>>

    /** Number of signed-in users currently in the in-app live meeting for this class (Firestore). */
    fun observeLiveClassPresence(classId: String): Flow<Int>

    suspend fun joinLiveClass(classId: String): Result<Unit>

    suspend fun leaveLiveClass(classId: String): Result<Unit>

    suspend fun uploadClassNote(classId: String, name: String, sizeBytes: Long): Result<Resource>

    suspend fun deleteClassNote(classId: String, noteId: String): Result<Unit>

    fun observeDashboard(): Flow<DashboardSnapshot>

    /** Emits current time every second for live countdowns and schedules. */
    fun observeServerClock(): Flow<Long>

    fun observeResources(groupId: String): Flow<List<Resource>>

    suspend fun uploadResource(
        groupId: String,
        name: String,
        sizeBytes: Long
    ): Result<Resource>

    suspend fun downloadResource(
        groupId: String,
        resourceId: String
    ): Result<Resource>

    suspend fun updateProfile(
        firstName: String,
        lastName: String,
        displayName: String,
        phoneNumber: String,
        avatarUrl: String
    ): Result<User>

    fun logout()
}
