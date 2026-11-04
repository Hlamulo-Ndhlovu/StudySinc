package com.studysync.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.studysync.R
import com.studysync.data.StudyTask
import com.studysync.data.local.StudySyncDatabase
import com.studysync.data.local.StudyTaskEntity
import com.studysync.notifications.StudySyncNotifications
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

object SyncState {
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
}

data class SyncOutcome(
    val pushed: Int = 0,
    val offline: Boolean = false,
    val error: String? = null
)

/**
 * Study planner that works with no network. Writes land in Room immediately.
 * When a connection comes back they are pushed to Firestore (`users/{uid}/tasks`)
 * and remote edits are pulled down.
 */
class OfflineStudyRepository(
    private val appContext: Context,
    database: StudySyncDatabase,
    private val notificationsEnabled: () -> Boolean
) {
    private val dao = database.studyTaskDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    fun start() {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        _online.value = hasInternet(cm)
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _online.value = true
                    scope.launch { syncNow() }
                }

                override fun onLost(network: Network) {
                    _online.value = hasInternet(cm)
                }
            }
        )
        Firebase.auth.addAuthStateListener { auth ->
            if (auth.currentUser != null && _online.value) {
                scope.launch { syncNow() }
            }
        }
    }

    fun observeTasks(uid: String): Flow<List<StudyTask>> =
        dao.observeActive(uid).map { rows -> rows.map { it.toModel() } }

    suspend fun addTask(uid: String, title: String, subject: String, notes: String, priority: Int) {
        val now = System.currentTimeMillis()
        dao.upsert(
            StudyTaskEntity(
                id = UUID.randomUUID().toString(),
                ownerUid = uid,
                title = title.trim(),
                subject = subject.trim().ifBlank { "General" },
                notes = notes.trim(),
                dueAt = now + DAY_MS,
                priority = priority.coerceIn(0, 2),
                done = false,
                updatedAt = now,
                syncState = SyncState.PENDING,
                everSynced = false,
                deleted = false
            )
        )
        if (_online.value) syncNow()
    }

    suspend fun setDone(taskId: String, done: Boolean) {
        val existing = dao.get(taskId) ?: return
        dao.upsert(
            existing.copy(
                done = done,
                updatedAt = System.currentTimeMillis(),
                syncState = SyncState.PENDING
            )
        )
        if (_online.value) syncNow()
    }

    suspend fun deleteTask(taskId: String) {
        val existing = dao.get(taskId) ?: return
        if (!existing.everSynced) {
            dao.deleteById(taskId)
        } else {
            dao.upsert(
                existing.copy(
                    deleted = true,
                    updatedAt = System.currentTimeMillis(),
                    syncState = SyncState.PENDING
                )
            )
        }
        if (_online.value) syncNow()
    }

    suspend fun syncNow(): SyncOutcome = syncMutex.withLock {
        val uid = Firebase.auth.currentUser?.uid
            ?: return SyncOutcome(error = "Sign in to sync")
        if (!_online.value) return SyncOutcome(offline = true)
        _syncing.value = true
        try {
            val pushed = pushPending(uid)
            pullRemote(uid)
            if (pushed > 0 && notificationsEnabled()) {
                StudySyncNotifications.showGeneral(
                    appContext,
                    appContext.getString(R.string.notification_sync_title),
                    appContext.getString(R.string.notification_sync_body, pushed)
                )
            }
            SyncOutcome(pushed = pushed)
        } catch (e: Exception) {
            SyncOutcome(error = e.message ?: "Sync failed")
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun pushPending(uid: String): Int {
        val pending = dao.pending(uid)
        var pushed = 0
        val collection = Firebase.firestore.collection("users").document(uid).collection("tasks")
        for (task in pending) {
            val doc = collection.document(task.id)
            if (task.deleted) {
                doc.delete().await()
                dao.deleteById(task.id)
            } else {
                doc.set(
                    mapOf(
                        "title" to task.title,
                        "subject" to task.subject,
                        "notes" to task.notes,
                        "dueAt" to task.dueAt,
                        "priority" to task.priority,
                        "done" to task.done,
                        "updatedAt" to task.updatedAt,
                        "ownerUid" to uid
                    ),
                    SetOptions.merge()
                ).await()
                dao.upsert(task.copy(syncState = SyncState.SYNCED, everSynced = true))
            }
            pushed++
        }
        return pushed
    }

    private suspend fun pullRemote(uid: String) {
        val snapshot = Firebase.firestore.collection("users").document(uid)
            .collection("tasks")
            .get()
            .await()
        val remoteIds = snapshot.documents.map { it.id }.toSet()
        for (doc in snapshot.documents) {
            val remote = doc.toEntity(uid) ?: continue
            val local = dao.get(doc.id)
            val keepLocalEdit = local != null && local.syncState != SyncState.SYNCED
            if (!keepLocalEdit && (local == null || remote.updatedAt >= local.updatedAt)) {
                dao.upsert(remote)
            }
        }
        for (local in dao.synced(uid)) {
            if (local.id !in remoteIds) dao.deleteById(local.id)
        }
    }

    private fun hasInternet(cm: ConnectivityManager): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun StudyTaskEntity.toModel() = StudyTask(
        id = id,
        title = title,
        subject = subject,
        notes = notes,
        dueAt = dueAt,
        priority = priority,
        done = done,
        pendingSync = syncState != SyncState.SYNCED
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toEntity(uid: String): StudyTaskEntity? {
        val title = getString("title") ?: return null
        return StudyTaskEntity(
            id = id,
            ownerUid = uid,
            title = title,
            subject = getString("subject").orEmpty(),
            notes = getString("notes").orEmpty(),
            dueAt = getLong("dueAt") ?: 0L,
            priority = (getLong("priority") ?: 1L).toInt(),
            done = getBoolean("done") ?: false,
            updatedAt = getLong("updatedAt") ?: 0L,
            syncState = SyncState.SYNCED,
            everSynced = true,
            deleted = false
        )
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
