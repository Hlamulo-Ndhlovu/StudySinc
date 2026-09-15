package com.studysync.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.studysync.data.ClassSession
import com.studysync.data.DashboardSnapshot
import com.studysync.data.Friend
import com.studysync.data.FriendInbound
import com.studysync.data.Message
import com.studysync.data.Resource
import com.studysync.data.StudyGroup
import com.studysync.data.StudyRepository
import com.studysync.data.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/**
 * Production backend: Firebase Auth plus Firestore with snapshot listeners so chats, groups,
 * classes, friends, resources, dashboard, and the signed-in user profile all update in real time.
 *
 * Deploy Firestore security rules and enable Email/Password and Google in Firebase Console.
 */
class FirebaseStudyRepository : StudyRepository {

    private val auth: FirebaseAuth = Firebase.auth
    private val db: FirebaseFirestore = Firebase.firestore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _clock = MutableStateFlow(System.currentTimeMillis())
    private val typingJobs = mutableMapOf<String, Job>()
    private var userProfileListener: ListenerRegistration? = null

    init {
        scope.launch {
            while (true) {
                delay(1_000L)
                _clock.value = System.currentTimeMillis()
            }
        }
        auth.addAuthStateListener { firebaseAuth ->
            userProfileListener?.remove()
            userProfileListener = null
            val firebaseUser = firebaseAuth.currentUser
            if (firebaseUser == null) {
                _currentUser.value = null
            } else {
                userProfileListener = db.collection(COL_USERS).document(firebaseUser.uid)
                    .addSnapshotListener { snap, e ->
                        if (e != null) {
                            _currentUser.value = firebaseUser.toUserFallback()
                            return@addSnapshotListener
                        }
                        _currentUser.value = if (snap != null && snap.exists()) {
                            snap.toUser(firebaseUser) ?: firebaseUser.toUserFallback()
                        } else {
                            firebaseUser.toUserFallback()
                        }
                    }
            }
        }
    }

    private suspend fun loadUserProfile(firebaseUser: FirebaseUser): User = withContext(Dispatchers.IO) {
        val snap = db.collection(COL_USERS).document(firebaseUser.uid).get().await()
        if (snap.exists()) snap.toUser(firebaseUser)!! else firebaseUser.toUserFallback()
    }

    override suspend fun login(email: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        runSuspendCatching {
            val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val u = result.user ?: throw IllegalStateException("No user")
            loadUserProfile(u)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        runSuspendCatching {
            auth.sendPasswordResetEmail(email.trim()).await()
            Unit
        }
    }

    override suspend fun register(
        email: String,
        password: String,
        displayName: String
    ): Result<User> = withContext(Dispatchers.IO) {
        runSuspendCatching {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = result.user ?: throw IllegalStateException("No user")
            val profile = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(displayName.ifBlank { email.substringBefore("@") })
                .build()
            firebaseUser.updateProfile(profile).await()
            val user = User(
                id = firebaseUser.uid,
                email = firebaseUser.email.orEmpty(),
                displayName = displayName.ifBlank { firebaseUser.email?.substringBefore("@").orEmpty() },
                firstName = "",
                lastName = "",
                phoneNumber = "",
                avatarUrl = ""
            )
            db.collection(COL_USERS).document(firebaseUser.uid).set(
                mapOf(
                    "email" to user.email.trim().lowercase(),
                    "displayName" to user.displayName,
                    "firstName" to "",
                    "lastName" to "",
                    "phoneNumber" to "",
                    "avatarUrl" to "",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            user
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<User> = withContext(Dispatchers.IO) {
        runSuspendCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val firebaseUser = result.user ?: throw IllegalStateException("No user")
            val ref = db.collection(COL_USERS).document(firebaseUser.uid)
            if (!ref.get().await().exists()) {
                val displayName =
                    firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@").orEmpty()
                ref.set(
                    mapOf(
                        "email" to firebaseUser.email.orEmpty().trim().lowercase(),
                        "displayName" to displayName,
                        "firstName" to "",
                        "lastName" to "",
                        "phoneNumber" to "",
                        "avatarUrl" to firebaseUser.photoUrl?.toString().orEmpty(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            loadUserProfile(firebaseUser)
        }
    }

    override fun observeGroups(): Flow<List<StudyGroup>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val reg: ListenerRegistration = db.collection(COL_GROUPS)
            .whereArrayContains(FIELD_MEMBER_IDS, uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toStudyGroup() } ?: emptyList()
                trySend(list.sortedByDescending { it.createdAt })
            }
        awaitClose { reg.remove() }
    }

    override fun observeGroup(groupId: String): Flow<StudyGroup?> = callbackFlow {
        val reg = db.collection(COL_GROUPS).document(groupId)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                trySend(snap?.toStudyGroup())
            }
        awaitClose { reg.remove() }
    }

    override suspend fun createGroup(name: String, subject: String): Result<StudyGroup> =
        withContext(Dispatchers.IO) {
            val uid = auth.currentUser?.uid ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
            runSuspendCatching {
                val ref = db.collection(COL_GROUPS).document()
                val group = StudyGroup(
                    id = ref.id,
                    name = name.trim(),
                    subject = subject.ifBlank { "General" },
                    memberCount = 1,
                    lastMessagePreview = "Welcome to ${name.trim()}!",
                    createdAt = System.currentTimeMillis(),
                    lastMessageAt = System.currentTimeMillis()
                )
                ref.set(
                    mapOf(
                        "name" to group.name,
                        "subject" to group.subject,
                        "memberCount" to 1,
                        "lastMessagePreview" to group.lastMessagePreview,
                        "createdAt" to group.createdAt,
                        "lastMessageAt" to group.lastMessageAt,
                        FIELD_MEMBER_IDS to listOf(uid)
                    )
                ).await()
                group
            }
        }

    override fun observeMessages(groupId: String): Flow<List<Message>> = callbackFlow {
        val reg = db.collection(COL_GROUPS).document(groupId).collection(COL_MESSAGES)
            .orderBy(FIELD_TIMESTAMP, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toMessage(groupId) } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    override fun observeTyping(groupId: String): Flow<String?> = callbackFlow {
        val uid = auth.currentUser?.uid
        val reg = db.collection(COL_GROUPS).document(groupId).collection(COL_TYPING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val others = snapshot?.documents.orEmpty()
                    .filter { it.id != uid }
                    .mapNotNull { it.getString("label") }
                trySend(others.firstOrNull())
            }
        awaitClose { reg.remove() }
    }

    override suspend fun reportTyping(groupId: String, isTyping: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val name = _currentUser.value?.displayName?.ifBlank { null } ?: "Someone"
        typingJobs[groupId]?.cancel()
        if (!isTyping) {
            db.collection(COL_GROUPS).document(groupId).collection(COL_TYPING).document(uid).delete()
            return
        }
        db.collection(COL_GROUPS).document(groupId).collection(COL_TYPING).document(uid).set(
            mapOf(
                "label" to "$name is typing…",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        typingJobs[groupId] = scope.launch {
            delay(2_800L)
            runSuspendCatching {
                db.collection(COL_GROUPS).document(groupId).collection(COL_TYPING).document(uid).delete()
            }
        }
    }

    override suspend fun sendMessage(groupId: String, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val sender = auth.currentUser ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
            val user = _currentUser.value
            val senderName = user?.displayName?.ifBlank { null } ?: sender.email?.substringBefore("@") ?: "User"
            if (content.isBlank()) return@withContext Result.failure(IllegalArgumentException("Empty message"))
            runSuspendCatching {
                val msgRef = db.collection(COL_GROUPS).document(groupId).collection(COL_MESSAGES).document()
                val batch = db.batch()
                batch.set(
                    msgRef,
                    mapOf(
                        "groupId" to groupId,
                        "senderId" to sender.uid,
                        "senderName" to senderName,
                        "content" to content.trim(),
                        FIELD_TIMESTAMP to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    db.collection(COL_GROUPS).document(groupId),
                    mapOf(
                        "lastMessagePreview" to content.trim().take(120),
                        "lastMessageAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
                reportTyping(groupId, false)
                Unit
            }
        }

    override fun observeFriends(): Flow<List<Friend>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val reg = db.collection(COL_USERS).document(uid).collection(COL_FRIENDS)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toFriend() } ?: emptyList()
                trySend(list.sortedByDescending { it.lastInteraction })
            }
        awaitClose { reg.remove() }
    }

    override fun observeFriendInbound(): Flow<List<FriendInbound>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val reg = db.collection(COL_USERS).document(uid).collection(COL_FRIEND_INBOUND)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toFriendInbound() }
                    ?.sortedByDescending { it.createdAtMillis }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    override suspend fun addFriend(name: String, email: String): Result<Friend> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        runSuspendCatching {
            val ref = db.collection(COL_USERS).document(uid).collection(COL_FRIENDS).document()
            val friend = Friend(
                id = ref.id,
                name = name.ifBlank { email.substringBefore("@") },
                email = email.lowercase(),
                isOnline = false,
                lastInteraction = System.currentTimeMillis()
            )
            ref.set(
                mapOf(
                    "name" to friend.name,
                    "email" to friend.email,
                    "isOnline" to false,
                    "lastInteraction" to friend.lastInteraction
                )
            ).await()

            val normalizedLookup = email.trim().lowercase()
            val rawTrim = email.trim()
            var targetDoc = db.collection(COL_USERS)
                .whereEqualTo("email", normalizedLookup)
                .limit(1)
                .get().await()
                .documents
                .firstOrNull()
            if (targetDoc == null && rawTrim != normalizedLookup) {
                targetDoc = db.collection(COL_USERS)
                    .whereEqualTo("email", rawTrim)
                    .limit(1)
                    .get().await()
                    .documents
                    .firstOrNull()
            }
            val targetUid = targetDoc?.id
            if (targetUid != null && targetUid != uid) {
                val myName = _currentUser.value?.displayName?.ifBlank { null }
                    ?: auth.currentUser?.email?.substringBefore("@")
                    ?: "Someone"
                val myEmail = auth.currentUser?.email?.trim()?.lowercase().orEmpty()
                db.collection(COL_USERS).document(targetUid).collection(COL_FRIEND_INBOUND).add(
                    mapOf(
                        "fromUid" to uid,
                        "fromDisplayName" to myName,
                        "fromEmail" to myEmail,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            friend
        }
    }

    override fun observeClasses(): Flow<List<ClassSession>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val reg = db.collection(COL_CLASSES)
            .whereEqualTo(FIELD_OWNER_UID, uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toClassSession() } ?: emptyList()
                trySend(list.sortedBy { it.startTimeMillis })
            }
        awaitClose { reg.remove() }
    }

    override fun observeClass(classId: String): Flow<ClassSession?> = callbackFlow {
        val reg = db.collection(COL_CLASSES).document(classId)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                trySend(snap?.toClassSession())
            }
        awaitClose { reg.remove() }
    }

    override suspend fun createClass(
        title: String,
        topic: String,
        startTimeMillis: Long,
        durationMinutes: Int,
        location: String,
        instructor: String,
        meetingLink: String,
        isLive: Boolean,
        description: String
    ): Result<ClassSession> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        runSuspendCatching {
            val existing = db.collection(COL_CLASSES)
                .whereEqualTo(FIELD_OWNER_UID, uid)
                .limit(1)
                .get()
                .await()
            if (!existing.isEmpty) {
                throw IllegalStateException("You already have a class. Delete it first to create another.")
            }
            val ref = db.collection(COL_CLASSES).document()
            val roomName = meetingLink.ifBlank { "room-${ref.id}" }
            val session = ClassSession(
                id = ref.id,
                title = title.ifBlank { "Untitled Class" },
                topic = topic.ifBlank { "General" },
                startTimeMillis = startTimeMillis,
                durationMinutes = durationMinutes,
                location = location.ifBlank { "Online" },
                instructor = instructor.ifBlank { "Instructor" },
                meetingLink = meetingLink.ifBlank { "https://meet.studysync.app/$roomName" },
                roomName = roomName,
                isLive = isLive,
                description = description,
                hostName = instructor.ifBlank { "Instructor" },
                attendeeCount = (5..29).random(),
                ownerUid = uid
            )
            ref.set(
                mapOf(
                    FIELD_OWNER_UID to uid,
                    "title" to session.title,
                    "topic" to session.topic,
                    "startTimeMillis" to session.startTimeMillis,
                    "durationMinutes" to session.durationMinutes,
                    "location" to session.location,
                    "instructor" to session.instructor,
                    "meetingLink" to session.meetingLink,
                    "roomName" to session.roomName,
                    "isLive" to session.isLive,
                    "description" to session.description,
                    "hostName" to session.hostName,
                    "attendeeCount" to session.attendeeCount
                )
            ).await()
            session
        }
    }

    override suspend fun updateClassLive(classId: String, isLive: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runSuspendCatching {
                db.collection(COL_CLASSES).document(classId).update("isLive", isLive).await()
                Unit
            }
        }

    override suspend fun deleteClass(classId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        runSuspendCatching {
            val classRef = db.collection(COL_CLASSES).document(classId)
            val snap = classRef.get().await()
            if (!snap.exists()) throw IllegalStateException("Class not found")
            if (snap.getString(FIELD_OWNER_UID) != uid) {
                throw IllegalStateException("Only the host can delete this class")
            }
            val notesSnap = classRef.collection(COL_NOTES).get().await()
            for (doc in notesSnap.documents) {
                doc.reference.delete().await()
            }
            classRef.delete().await()
            Unit
        }
    }

    override fun observeClassNotes(classId: String): Flow<List<Resource>> = callbackFlow {
        val reg = db.collection(COL_CLASSES).document(classId).collection(COL_NOTES)
            .orderBy(FIELD_UPLOADED_AT, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toResource() } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    override fun observeLiveClassPresence(classId: String): Flow<Int> = callbackFlow {
        if (classId.isBlank()) {
            trySend(0)
            awaitClose { }
            return@callbackFlow
        }
        val reg = db.collection(COL_CLASSES).document(classId).collection(COL_LIVE_PRESENCE)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.size ?: 0)
            }
        awaitClose { reg.remove() }
    }

    override suspend fun joinLiveClass(classId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        if (classId.isBlank()) return@withContext Result.success(Unit)
        runSuspendCatching {
            val name = _currentUser.value?.displayName?.ifBlank { null }
                ?: auth.currentUser?.email?.substringBefore("@")
                ?: "Someone"
            db.collection(COL_CLASSES).document(classId).collection(COL_LIVE_PRESENCE).document(uid)
                .set(
                    mapOf(
                        "displayName" to name,
                        "joinedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Unit
        }
    }

    override suspend fun leaveLiveClass(classId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.success(Unit)
        if (classId.isBlank()) return@withContext Result.success(Unit)
        runSuspendCatching {
            db.collection(COL_CLASSES).document(classId).collection(COL_LIVE_PRESENCE).document(uid)
                .delete().await()
            Unit
        }
    }

    override suspend fun uploadClassNote(classId: String, name: String, sizeBytes: Long): Result<Resource> =
        withContext(Dispatchers.IO) {
            val owner = auth.currentUser ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
            val ownerName = _currentUser.value?.displayName?.ifBlank { null }
                ?: owner.email?.substringBefore("@") ?: "User"
            runSuspendCatching {
                val ref = db.collection(COL_CLASSES).document(classId).collection(COL_NOTES).document()
                val res = Resource(
                    id = ref.id,
                    name = name.ifBlank { "Note" },
                    sizeBytes = sizeBytes.coerceAtLeast(1_024L),
                    ownerName = ownerName,
                    uploadedAt = System.currentTimeMillis(),
                    groupId = null,
                    classId = classId,
                    ownerUid = owner.uid
                )
                ref.set(
                    mapOf(
                        "name" to res.name,
                        "sizeBytes" to res.sizeBytes,
                        "ownerName" to res.ownerName,
                        "ownerUid" to owner.uid,
                        FIELD_UPLOADED_AT to FieldValue.serverTimestamp(),
                        "classId" to classId
                    )
                ).await()
                res.copy(uploadedAt = System.currentTimeMillis())
            }
        }

    override suspend fun deleteClassNote(classId: String, noteId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runSuspendCatching {
                db.collection(COL_CLASSES).document(classId).collection(COL_NOTES).document(noteId)
                    .delete().await()
                Unit
            }
        }

    override fun observeDashboard(): Flow<DashboardSnapshot> =
        combine(
            combine(
                observeGroups(),
                observeFriends(),
                observeClasses(),
                observeRecentMessages(),
                observeRecentResources()
            ) { g, f, c, rm, rr ->
                DashboardInputs(g, f, c, rm, rr)
            },
            _clock
        ) { inputs, now ->
            val groups = inputs.groups
            val friends = inputs.friends
            val classes = inputs.classes
            val recentMessages = inputs.recentMessages
            val recentResources = inputs.recentResources
            val nextClass = classes.filter { it.startTimeMillis >= now }
                .minByOrNull { it.startTimeMillis }
            val onlineFriendsCount = friends.count { it.isOnline }
            val activeGroupsCount = groups.count { g ->
                val last = g.lastMessageAt ?: 0L
                now - last < 300_000L
            }
            DashboardSnapshot(
                nextClass = nextClass,
                totalClasses = classes.size,
                totalFriends = friends.size,
                totalGroups = groups.size,
                onlineFriendsCount = onlineFriendsCount,
                activeGroupsCount = activeGroupsCount,
                recentMessages = recentMessages,
                spotlightGroups = groups.sortedByDescending { it.createdAt }.take(3),
                recentResources = recentResources,
                nowMillis = now
            )
        }

    private data class DashboardInputs(
        val groups: List<StudyGroup>,
        val friends: List<Friend>,
        val classes: List<ClassSession>,
        val recentMessages: List<Message>,
        val recentResources: List<Resource>
    )

    private fun observeRecentMessages(): Flow<List<Message>> = callbackFlow {
        val reg = db.collectionGroup("messages")
            .orderBy(FIELD_TIMESTAMP, Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    val gid = doc.getString("groupId") ?: doc.reference.parent.parent?.id
                    gid?.let { doc.toMessage(it) }
                } ?: emptyList()
                trySend(list.sortedByDescending { it.timestamp }.take(5))
            }
        awaitClose { reg.remove() }
    }

    private fun observeRecentResources(): Flow<List<Resource>> = callbackFlow {
        val reg = db.collectionGroup("resources")
            .orderBy(FIELD_UPLOADED_AT, Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toResource() } ?: emptyList()
                trySend(list.take(5))
            }
        awaitClose { reg.remove() }
    }

    override fun observeServerClock(): Flow<Long> = _clock.asStateFlow()

    override fun observeResources(groupId: String): Flow<List<Resource>> = callbackFlow {
        val reg = db.collection(COL_GROUPS).document(groupId).collection(COL_RESOURCES)
            .orderBy(FIELD_UPLOADED_AT, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toResource() } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    override suspend fun uploadResource(
        groupId: String,
        name: String,
        sizeBytes: Long
    ): Result<Resource> = withContext(Dispatchers.IO) {
        val owner = auth.currentUser ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        val ownerName = _currentUser.value?.displayName?.ifBlank { null }
            ?: owner.email?.substringBefore("@") ?: "User"
        runSuspendCatching {
            val ref = db.collection(COL_GROUPS).document(groupId).collection(COL_RESOURCES).document()
            val res = Resource(
                id = ref.id,
                name = name.ifBlank { "Resource" },
                sizeBytes = sizeBytes.coerceAtLeast(1_024L),
                ownerName = ownerName,
                uploadedAt = System.currentTimeMillis(),
                groupId = groupId,
                classId = null,
                ownerUid = owner.uid
            )
            ref.set(
                mapOf(
                    "name" to res.name,
                    "sizeBytes" to res.sizeBytes,
                    "ownerName" to res.ownerName,
                    "ownerUid" to owner.uid,
                    FIELD_UPLOADED_AT to FieldValue.serverTimestamp(),
                    "groupId" to groupId
                )
            ).await()
            res.copy(uploadedAt = System.currentTimeMillis())
        }
    }

    override suspend fun downloadResource(groupId: String, resourceId: String): Result<Resource> =
        withContext(Dispatchers.IO) {
            runSuspendCatching {
                val snap = db.collection(COL_GROUPS).document(groupId).collection(COL_RESOURCES)
                    .document(resourceId).get().await()
                snap.toResource() ?: throw IllegalStateException("Resource not found")
            }
        }

    override suspend fun updateProfile(
        firstName: String,
        lastName: String,
        displayName: String,
        phoneNumber: String,
        avatarUrl: String
    ): Result<User> = withContext(Dispatchers.IO) {
        val firebaseUser = auth.currentUser ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
        runSuspendCatching {
            val disp = displayName.ifBlank {
                buildString {
                    if (firstName.isNotBlank()) append(firstName.trim())
                    if (lastName.isNotBlank()) {
                        if (isNotEmpty()) append(" ")
                        append(lastName.trim())
                    }
                    if (isEmpty()) append(firebaseUser.email?.substringBefore("@").orEmpty())
                }
            }
            val profile = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(disp)
                .build()
            firebaseUser.updateProfile(profile).await()
            db.collection(COL_USERS).document(firebaseUser.uid).set(
                mapOf(
                    "email" to firebaseUser.email.orEmpty().trim().lowercase(),
                    "displayName" to disp,
                    "firstName" to firstName,
                    "lastName" to lastName,
                    "phoneNumber" to phoneNumber,
                    "avatarUrl" to avatarUrl
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
            User(
                id = firebaseUser.uid,
                email = firebaseUser.email.orEmpty(),
                displayName = disp,
                firstName = firstName,
                lastName = lastName,
                phoneNumber = phoneNumber,
                avatarUrl = avatarUrl
            )
            // _currentUser is updated by the Firestore snapshot listener
        }
    }

    override fun logout() {
        userProfileListener?.remove()
        userProfileListener = null
        auth.signOut()
        _currentUser.value = null
    }

    private fun FirebaseUser.toUserFallback(): User = User(
        id = uid,
        email = email.orEmpty(),
        displayName = displayName ?: email?.substringBefore("@").orEmpty(),
        firstName = "",
        lastName = "",
        phoneNumber = "",
        avatarUrl = photoUrl?.toString().orEmpty()
    )

    companion object {
        private const val COL_USERS = "users"
        private const val COL_GROUPS = "groups"
        private const val COL_MESSAGES = "messages"
        private const val COL_TYPING = "typing"
        private const val COL_FRIENDS = "friends"
        private const val COL_CLASSES = "classes"
        private const val COL_RESOURCES = "resources"
        private const val COL_NOTES = "notes"
        private const val COL_LIVE_PRESENCE = "livePresence"
        private const val COL_FRIEND_INBOUND = "friendInbound"
        private const val FIELD_MEMBER_IDS = "memberIds"
        private const val FIELD_TIMESTAMP = "timestamp"
        private const val FIELD_OWNER_UID = "ownerUid"
        private const val FIELD_UPLOADED_AT = "uploadedAt"
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toUser(firebaseUser: FirebaseUser): User? {
    return User(
        id = id,
        email = getString("email") ?: firebaseUser.email.orEmpty(),
        displayName = getString("displayName") ?: firebaseUser.displayName.orEmpty(),
        firstName = getString("firstName").orEmpty(),
        lastName = getString("lastName").orEmpty(),
        phoneNumber = getString("phoneNumber").orEmpty(),
        avatarUrl = getString("avatarUrl").orEmpty()
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.toStudyGroup(): StudyGroup? {
    val created = getLong("createdAt") ?: getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
    val lastAt = getLong("lastMessageAt") ?: getTimestamp("lastMessageAt")?.toDate()?.time
    return StudyGroup(
        id = id,
        name = getString("name").orEmpty(),
        subject = getString("subject").orEmpty(),
        memberCount = (getLong("memberCount") ?: 1L).toInt(),
        lastMessagePreview = getString("lastMessagePreview"),
        createdAt = created,
        lastMessageAt = lastAt
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.toMessage(fallbackGroupId: String): Message? {
    val groupId = getString("groupId") ?: fallbackGroupId
    val ts = getTimestamp(FIELD_TIMESTAMP)?.toDate()?.time ?: getLong("timestamp") ?: System.currentTimeMillis()
    return Message(
        id = this.id,
        groupId = groupId,
        senderId = getString("senderId").orEmpty(),
        senderName = getString("senderName").orEmpty(),
        content = getString("content").orEmpty(),
        timestamp = ts
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.toFriendInbound(): FriendInbound? {
    val fromUid = getString("fromUid") ?: return null
    val created = getTimestamp("createdAt")?.toDate()?.time
        ?: getLong("createdAt")
        ?: System.currentTimeMillis()
    return FriendInbound(
        id = id,
        fromUid = fromUid,
        fromDisplayName = getString("fromDisplayName").orEmpty(),
        fromEmail = getString("fromEmail").orEmpty(),
        createdAtMillis = created
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.toFriend(): Friend? {
    return Friend(
        id = id,
        name = getString("name").orEmpty(),
        email = getString("email").orEmpty(),
        isOnline = getBoolean("isOnline") ?: false,
        lastInteraction = getLong("lastInteraction") ?: System.currentTimeMillis()
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.toClassSession(): ClassSession? {
    return ClassSession(
        id = id,
        title = getString("title").orEmpty(),
        topic = getString("topic").orEmpty(),
        startTimeMillis = getLong("startTimeMillis") ?: 0L,
        durationMinutes = (getLong("durationMinutes") ?: 60L).toInt(),
        location = getString("location").orEmpty(),
        instructor = getString("instructor").orEmpty(),
        attendeeCount = (getLong("attendeeCount") ?: 0L).toInt(),
        meetingLink = getString("meetingLink").orEmpty(),
        roomName = getString("roomName").orEmpty(),
        hostName = getString("hostName") ?: getString("instructor").orEmpty(),
        isLive = getBoolean("isLive") ?: false,
        description = getString("description").orEmpty(),
        ownerUid = getString("ownerUid").orEmpty()
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.toResource(): Resource? {
    val uploaded = getLong(FIELD_UPLOADED_AT) ?: getTimestamp(FIELD_UPLOADED_AT)?.toDate()?.time ?: System.currentTimeMillis()
    val parentColl = reference.parent.id
    val resolvedGroupId = when {
        parentColl == "resources" -> getString("groupId") ?: reference.parent.parent?.id
        else -> getString("groupId")
    }
    val resolvedClassId = when {
        parentColl == "notes" -> getString("classId") ?: reference.parent.parent?.id
        else -> getString("classId")
    }
    return Resource(
        id = this.id,
        name = getString("name").orEmpty(),
        sizeBytes = getLong("sizeBytes") ?: 0L,
        ownerName = getString("ownerName").orEmpty(),
        uploadedAt = uploaded,
        groupId = resolvedGroupId,
        classId = resolvedClassId,
        ownerUid = getString("ownerUid").orEmpty()
    )
}

private const val FIELD_TIMESTAMP = "timestamp"
private const val FIELD_UPLOADED_AT = "uploadedAt"
