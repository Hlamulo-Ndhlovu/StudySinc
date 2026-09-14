package com.studysync.data

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/**
 * In-memory repository for tests and previews only.
 * The production app uses [com.studysync.data.firebase.FirebaseStudyRepository] for live sync.
 */
class InMemoryStudyRepository : StudyRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val credentialStore = mutableMapOf<String, Pair<String, User>>()
    private val random = Random(System.currentTimeMillis())

    private val responders = listOf(
        "u-mentor-1" to "Ava",
        "u-mentor-2" to "Leo",
        "u-mentor-3" to "Sam",
        "u-mentor-4" to "Mia"
    )

    private val cannedReplies = listOf(
        "Great point—can you share a link?",
        "Check the lecture slides from week 4.",
        "I can pair later today if needed.",
        "Remember to cite your sources.",
        "Let's add this to the doc.",
        "I recorded a quick Loom on this."
    )

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser = _currentUser.asStateFlow()

    private val _groups = MutableStateFlow<List<StudyGroup>>(emptyList())
    private val _messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    private val _friends = MutableStateFlow(seedFriends())
    private val _classes = MutableStateFlow<List<ClassSession>>(emptyList())
    private val _classNotes = MutableStateFlow<Map<String, List<Resource>>>(emptyMap())
    private val _resources = MutableStateFlow<Map<String, List<Resource>>>(emptyMap())
    private val _clock = MutableStateFlow(System.currentTimeMillis())
    private val _typingLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    private val typingJobs = ConcurrentHashMap<String, Job>()
    private val _liveClassPresence = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    init {
        scope.launch {
            mutex.withLock {
                val groups = seedGroups()
                _groups.value = groups
                val starter = mutableMapOf<String, List<Message>>()
                val t = System.currentTimeMillis()
                for (g in groups) {
                    starter[g.id] = listOf(
                        Message(
                            id = UUID.randomUUID().toString(),
                            groupId = g.id,
                            senderId = "system",
                            senderName = "StudySync",
                            content = "New messages in this group appear in real time.",
                            timestamp = t - 120_000
                        )
                    )
                }
                _messages.value = starter
                _groups.value = groups.map { g ->
                    val lastMsg = starter[g.id]?.lastOrNull()
                    if (lastMsg != null) {
                        g.copy(
                            lastMessagePreview = lastMsg.content,
                            lastMessageAt = lastMsg.timestamp
                        )
                    } else g
                }
            }
        }
        scope.launch {
            while (true) {
                delay(1_000L)
                _clock.value = System.currentTimeMillis()
            }
        }
        scope.launch {
            while (true) {
                delay(8_000L)
                jitterFriendPresence()
            }
        }
    }

    override fun observeServerClock(): Flow<Long> = _clock

    override fun observeTyping(groupId: String): Flow<String?> =
        _typingLabels.map { it[groupId] }

    override suspend fun reportTyping(groupId: String, isTyping: Boolean) {
        if (_currentUser.value == null) return
        typingJobs[groupId]?.cancel()
        if (!isTyping) {
            mutex.withLock {
                _typingLabels.value = _typingLabels.value - groupId
            }
            return
        }
        val peerName = responders.random(random).second
        mutex.withLock {
            _typingLabels.value = _typingLabels.value + (groupId to "$peerName is typing…")
        }
        val job = scope.launch {
            delay(2_800L)
            mutex.withLock {
                if (_typingLabels.value[groupId]?.startsWith(peerName) == true) {
                    _typingLabels.value = _typingLabels.value - groupId
                }
            }
        }
        typingJobs[groupId] = job
    }

    private suspend fun jitterFriendPresence() {
        mutex.withLock {
            _friends.value = _friends.value.map { f ->
                f.copy(
                    isOnline = when {
                        random.nextFloat() < 0.1f -> !f.isOnline
                        random.nextFloat() < 0.4f -> true
                        else -> f.isOnline
                    },
                    lastInteraction = if (random.nextFloat() < 0.12f) {
                        System.currentTimeMillis()
                    } else {
                        f.lastInteraction
                    }
                )
            }
        }
    }

    override suspend fun login(email: String, password: String): Result<User> =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val record = credentialStore[email.lowercase()]
                    ?: return@withContext Result.failure(IllegalStateException("Account not found"))
                if (record.first != password) {
                    return@withContext Result.failure(IllegalStateException("Incorrect password"))
                }
                _currentUser.value = record.second
                Result.success(record.second)
            }
        }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        withContext(Dispatchers.Default) {
            Result.success(Unit)
        }

    override suspend fun register(
        email: String,
        password: String,
        displayName: String
    ): Result<User> = withContext(Dispatchers.Default) {
        mutex.withLock {
            val normalized = email.lowercase()
            if (credentialStore.containsKey(normalized)) {
                return@withContext Result.failure(IllegalStateException("Email already registered"))
            }
            val user = User(
                id = UUID.randomUUID().toString(),
                email = normalized,
                displayName = displayName.ifBlank { normalized.substringBefore("@") }
            )
            credentialStore[normalized] = password to user
            _currentUser.value = user
            Result.success(user)
        }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<User> =
        withContext(Dispatchers.Default) {
            Result.failure(
                IllegalStateException("Google sign-in is only available with Firebase. Use email/password.")
            )
        }

    override fun observeGroups(): Flow<List<StudyGroup>> = _groups

    override fun observeGroup(groupId: String): Flow<StudyGroup?> =
        _groups.map { groups -> groups.firstOrNull { it.id == groupId } }

    override suspend fun createGroup(name: String, subject: String): Result<StudyGroup> =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val current = _groups.value
                val group = StudyGroup(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    subject = subject.ifBlank { "General" },
                    memberCount = 1,
                    lastMessagePreview = "Welcome to $name!"
                )
                _groups.value = current + group
                _messages.value = _messages.value + (group.id to listOf())
                Result.success(group)
            }
        }

    override fun observeMessages(groupId: String): Flow<List<Message>> =
        _messages.map { messages ->
            messages[groupId].orEmpty().sortedBy { it.timestamp }
        }

    override suspend fun sendMessage(groupId: String, content: String): Result<Unit> {
        reportTyping(groupId, false)
        return sendMessageInner(groupId, content)
    }

    private suspend fun sendMessageInner(groupId: String, content: String): Result<Unit> =
        withContext(Dispatchers.Default) {
            if (content.isBlank()) return@withContext Result.failure(IllegalArgumentException("Empty message"))
            val sender = _currentUser.value
                ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))

            val newMessage = Message(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                senderId = sender.id,
                senderName = sender.displayName,
                content = content.trim()
            )
            appendMessage(newMessage)
            scheduleSyntheticReply(groupId)
            Result.success(Unit)
        }

    override fun logout() {
        _currentUser.value = null
    }

    override suspend fun updateProfile(
        firstName: String,
        lastName: String,
        displayName: String,
        phoneNumber: String,
        avatarUrl: String
    ): Result<User> = withContext(Dispatchers.Default) {
        mutex.withLock {
            val current = _currentUser.value ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
            val updated = current.copy(
                displayName = displayName.ifBlank {
                    buildString {
                        if (firstName.isNotBlank()) append(firstName.trim())
                        if (lastName.isNotBlank()) {
                            if (isNotEmpty()) append(" ")
                            append(lastName.trim())
                        }
                        if (isEmpty()) append(current.email.substringBefore("@"))
                    }
                },
                firstName = firstName,
                lastName = lastName,
                phoneNumber = phoneNumber,
                avatarUrl = avatarUrl
            )
            _currentUser.value = updated
            // keep credential store password the same, update user object
            val entry = credentialStore[updated.email]
            if (entry != null) {
                credentialStore[updated.email] = entry.first to updated
            }
            Result.success(updated)
        }
    }

    override fun observeFriends(): Flow<List<Friend>> = _friends

    override fun observeFriendInbound(): Flow<List<FriendInbound>> = flowOf(emptyList())

    override suspend fun addFriend(name: String, email: String): Result<Friend> =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val normalized = email.lowercase()
                if (_friends.value.any { it.email == normalized }) {
                    return@withContext Result.failure(IllegalStateException("Already added"))
                }
                val friend = Friend(
                    id = UUID.randomUUID().toString(),
                    name = name.ifBlank { normalized.substringBefore("@").replaceFirstChar { it.titlecase() } },
                    email = normalized,
                    isOnline = random.nextBoolean(),
                    lastInteraction = System.currentTimeMillis()
                )
                _friends.value = _friends.value + friend
                Result.success(friend)
            }
        }

    override fun observeClasses(): Flow<List<ClassSession>> = _classes

    override fun observeClass(classId: String): Flow<ClassSession?> =
        _classes.map { list -> list.firstOrNull { it.id == classId } }

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
    ): Result<ClassSession> = withContext(Dispatchers.Default) {
        mutex.withLock {
            val ownerUid = _currentUser.value?.id.orEmpty()
            if (ownerUid.isNotBlank() && _classes.value.any { it.ownerUid == ownerUid }) {
                return@withContext Result.failure(
                    IllegalStateException("You already have a class. Delete it first to create another.")
                )
            }
            if (ownerUid.isBlank() && _classes.value.isNotEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("You already have a class. Delete it first to create another.")
                )
            }
            val roomName = meetingLink.ifBlank { "room-${UUID.randomUUID()}" }
            val session = ClassSession(
                id = UUID.randomUUID().toString(),
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
                attendeeCount = random.nextInt(5, 30),
                ownerUid = ownerUid
            )
            _classes.value = (_classes.value + session).sortedBy { it.startTimeMillis }
            Result.success(session)
        }
    }

    override suspend fun updateClassLive(classId: String, isLive: Boolean): Result<Unit> =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val updated = _classes.value.map { cls ->
                    if (cls.id == classId) cls.copy(isLive = isLive) else cls
                }
                _classes.value = updated
                Result.success(Unit)
            }
        }

    override suspend fun deleteClass(classId: String): Result<Unit> =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val uid = _currentUser.value?.id ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
                val cls = _classes.value.firstOrNull { it.id == classId }
                    ?: return@withContext Result.failure(IllegalStateException("Class not found"))
                if (cls.ownerUid.isNotBlank() && cls.ownerUid != uid) {
                    return@withContext Result.failure(IllegalStateException("Only the host can delete this class"))
                }
                _classes.value = _classes.value.filter { it.id != classId }
                _classNotes.value = _classNotes.value - classId
                Result.success(Unit)
            }
        }

    override fun observeClassNotes(classId: String): Flow<List<Resource>> =
        _classNotes.map { map ->
            map[classId].orEmpty().sortedByDescending { it.uploadedAt }
        }

    override fun observeLiveClassPresence(classId: String): Flow<Int> =
        _liveClassPresence.map { m ->
            if (classId.isBlank()) 0 else m[classId]?.size ?: 0
        }

    override suspend fun joinLiveClass(classId: String): Result<Unit> =
        withContext(Dispatchers.Default) {
            if (classId.isBlank()) return@withContext Result.success(Unit)
            mutex.withLock {
                val uid = _currentUser.value?.id
                    ?: return@withLock Result.failure(IllegalStateException("Not authenticated"))
                val next = _liveClassPresence.value.toMutableMap()
                val set = (next[classId] ?: emptySet()).toMutableSet()
                set.add(uid)
                next[classId] = set.toSet()
                _liveClassPresence.value = next
                Result.success(Unit)
            }
        }

    override suspend fun leaveLiveClass(classId: String): Result<Unit> =
        withContext(Dispatchers.Default) {
            if (classId.isBlank()) return@withContext Result.success(Unit)
            mutex.withLock {
                val uid = _currentUser.value?.id ?: return@withLock Result.success(Unit)
                val next = _liveClassPresence.value.toMutableMap()
                val set = (next[classId] ?: emptySet()).toMutableSet()
                set.remove(uid)
                if (set.isEmpty()) next.remove(classId) else next[classId] = set.toSet()
                _liveClassPresence.value = next
                Result.success(Unit)
            }
        }

    override suspend fun uploadClassNote(classId: String, name: String, sizeBytes: Long): Result<Resource> =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val owner = _currentUser.value ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
                val res = Resource(
                    id = UUID.randomUUID().toString(),
                    name = name.ifBlank { "Note" },
                    sizeBytes = sizeBytes.coerceAtLeast(1_024L),
                    ownerName = owner.displayName.ifBlank { owner.email.substringBefore("@") },
                    uploadedAt = System.currentTimeMillis(),
                    groupId = null,
                    classId = classId,
                    ownerUid = owner.id
                )
                val current = _classNotes.value[classId].orEmpty()
                _classNotes.value = _classNotes.value + (classId to (current + res))
                Result.success(res)
            }
        }

    override suspend fun deleteClassNote(classId: String, noteId: String): Result<Unit> =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val list = _classNotes.value[classId].orEmpty()
                val next = list.filterNot { it.id == noteId }
                _classNotes.value = _classNotes.value + (classId to next)
                Result.success(Unit)
            }
        }

    override fun observeDashboard(): Flow<DashboardSnapshot> =
        combine(
            combine(_groups, _messages, _friends, _classes, _resources) { g, m, f, c, r ->
                DashboardDeps(g, m, f, c, r)
            },
            _clock
        ) { deps, now ->
            val groups = deps.groups
            val messages = deps.messages
            val friends = deps.friends
            val classes = deps.classes
            val resources = deps.resources
            val recentMessages = messages.values.flatten().sortedByDescending { it.timestamp }.take(5)
            val spotlightGroups = groups.sortedByDescending { it.createdAt }.take(3)
            val nextClass = classes.filter { it.startTimeMillis >= now }
                .minByOrNull { it.startTimeMillis }
            val onlineFriendsCount = friends.count { it.isOnline }
            val activeGroupsCount = groups.count { g ->
                val last = messages[g.id].orEmpty().maxOfOrNull { it.timestamp }
                    ?: g.lastMessageAt
                    ?: 0L
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
                spotlightGroups = spotlightGroups,
                recentResources = resources.values.flatten().sortedByDescending { it.uploadedAt }.take(5),
                nowMillis = now
            )
        }

    private data class DashboardDeps(
        val groups: List<StudyGroup>,
        val messages: Map<String, List<Message>>,
        val friends: List<Friend>,
        val classes: List<ClassSession>,
        val resources: Map<String, List<Resource>>
    )

    override fun observeResources(groupId: String): Flow<List<Resource>> =
        _resources.map { map ->
            map[groupId].orEmpty().sortedByDescending { it.uploadedAt }
        }

    override suspend fun uploadResource(
        groupId: String,
        name: String,
        sizeBytes: Long
    ): Result<Resource> = withContext(Dispatchers.Default) {
        mutex.withLock {
            val owner = _currentUser.value ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))
            val res = Resource(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "Resource" },
                sizeBytes = sizeBytes.coerceAtLeast(1_024L),
                ownerName = owner.displayName.ifBlank { owner.email.substringBefore("@") },
                uploadedAt = System.currentTimeMillis(),
                groupId = groupId,
                classId = null,
                ownerUid = owner.id
            )
            val current = _resources.value[groupId].orEmpty()
            _resources.value = _resources.value + (groupId to (current + res))
            Result.success(res)
        }
    }

    override suspend fun downloadResource(groupId: String, resourceId: String): Result<Resource> =
        withContext(Dispatchers.Default) {
            val res = _resources.value[groupId]?.firstOrNull { it.id == resourceId }
                ?: return@withContext Result.failure(IllegalStateException("Resource not found"))
            Result.success(res)
        }

    private suspend fun appendMessage(message: Message) {
        mutex.withLock {
            val current = _messages.value[message.groupId].orEmpty()
            _messages.value = _messages.value + (message.groupId to (current + message))

            // Update preview + member count heuristically.
            _groups.value = _groups.value.map { group ->
                if (group.id == message.groupId) {
                    group.copy(
                        memberCount = maxOf(group.memberCount, 3),
                        lastMessagePreview = message.content,
                        lastMessageAt = message.timestamp
                    )
                } else group
            }
        }
    }

    private fun scheduleSyntheticReply(groupId: String) {
        scope.launch {
            delay(random.nextLong(1_000, 3_500))
            val (responderId, responderName) = responders.random(random)
            val botMessage = Message(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                senderId = responderId,
                senderName = responderName,
                content = cannedReplies.random(random),
                timestamp = System.currentTimeMillis() + random.nextLong(500)
            )
            appendMessage(botMessage)
        }
    }

    private fun seedGroups(): List<StudyGroup> {
        return listOf(
            StudyGroup(
                id = "grp-android",
                name = "Android Patterns",
                subject = "Mobile",
                memberCount = 4,
                lastMessagePreview = "Let's cover coroutines + flows next."
            ),
            StudyGroup(
                id = "grp-algos",
                name = "Algorithms II",
                subject = "CS",
                memberCount = 5,
                lastMessagePreview = "Homework 3 due Tuesday."
            ),
            StudyGroup(
                id = "grp-systems",
                name = "Distributed Systems",
                subject = "Systems",
                memberCount = 6,
                lastMessagePreview = "Readings: Raft + Paxos."
            )
        )
    }

    private fun seedMessages(): Map<String, List<Message>> = emptyMap()

    private fun seedResources(): Map<String, List<Resource>> = emptyMap()

    private fun seedFriends(): List<Friend> = listOf(
        Friend(
            id = "f1",
            name = "Jordan Lee",
            email = "jordan@campus.edu",
            isOnline = true,
            lastInteraction = System.currentTimeMillis() - 30_000
        ),
        Friend(
            id = "f2",
            name = "Priya Patel",
            email = "priya@campus.edu",
            isOnline = false,
            lastInteraction = System.currentTimeMillis() - 180_000
        ),
        Friend(
            id = "f3",
            name = "Diego Martinez",
            email = "diego@campus.edu",
            isOnline = true,
            lastInteraction = System.currentTimeMillis() - 400_000
        )
    )

    private fun seedClasses(): List<ClassSession> = emptyList()
}
