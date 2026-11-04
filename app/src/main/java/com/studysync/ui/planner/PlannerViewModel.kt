package com.studysync.ui.planner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studysync.R
import com.studysync.data.StudyRepository
import com.studysync.data.StudyTask
import com.studysync.data.sync.OfflineStudyRepository
import com.studysync.notifications.StudySyncNotifications
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlannerUiState(
    val tasks: List<StudyTask> = emptyList(),
    val online: Boolean = true,
    val syncing: Boolean = false,
    val dialogOpen: Boolean = false,
    val draftTitle: String = "",
    val draftSubject: String = "",
    val draftNotes: String = "",
    val draftPriority: Int = 1,
    val message: String? = null,
    val signedIn: Boolean = false
)

class PlannerViewModel(
    private val appContext: Context,
    private val offline: OfflineStudyRepository,
    private val repository: StudyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlannerUiState())
    val state: StateFlow<PlannerUiState> = _state
    private var taskJob: Job? = null

    init {
        viewModelScope.launch {
            offline.online.collect { online -> _state.update { it.copy(online = online) } }
        }
        viewModelScope.launch {
            offline.syncing.collect { syncing -> _state.update { it.copy(syncing = syncing) } }
        }
        viewModelScope.launch {
            repository.currentUser.collectLatest { user ->
                taskJob?.cancel()
                if (user == null) {
                    _state.update { it.copy(tasks = emptyList(), signedIn = false) }
                } else {
                    _state.update { it.copy(signedIn = true) }
                    taskJob = viewModelScope.launch {
                        offline.observeTasks(user.id).collect { tasks ->
                            _state.update { it.copy(tasks = tasks) }
                        }
                    }
                }
            }
        }
    }

    fun onTitle(value: String) = _state.update { it.copy(draftTitle = value) }
    fun onSubject(value: String) = _state.update { it.copy(draftSubject = value) }
    fun onNotes(value: String) = _state.update { it.copy(draftNotes = value) }
    fun onPriority(value: Int) = _state.update { it.copy(draftPriority = value) }
    fun setDialog(open: Boolean) = _state.update { it.copy(dialogOpen = open, message = null) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun saveTask() {
        val draft = _state.value
        val uid = repository.currentUser.value?.id
        if (uid == null) {
            _state.update { it.copy(message = "Sign in to save a task") }
            return
        }
        if (draft.draftTitle.isBlank()) {
            _state.update { it.copy(message = "Add a title") }
            return
        }
        viewModelScope.launch {
            offline.addTask(uid, draft.draftTitle, draft.draftSubject, draft.draftNotes, draft.draftPriority)
            _state.update {
                it.copy(
                    dialogOpen = false,
                    draftTitle = "",
                    draftSubject = "",
                    draftNotes = "",
                    draftPriority = 1,
                    message = if (offline.online.value) {
                        appContext.getString(R.string.planner_sync_result, 1)
                    } else {
                        appContext.getString(R.string.planner_sync_offline)
                    }
                )
            }
        }
    }

    fun setDone(task: StudyTask, done: Boolean) {
        viewModelScope.launch { offline.setDone(task.id, done) }
    }

    fun delete(task: StudyTask) {
        viewModelScope.launch { offline.deleteTask(task.id) }
    }

    fun syncNow() {
        viewModelScope.launch {
            val outcome = offline.syncNow()
            val message = when {
                outcome.offline -> appContext.getString(R.string.planner_sync_offline)
                outcome.error != null -> outcome.error
                else -> appContext.getString(R.string.planner_sync_result, outcome.pushed)
            }
            _state.update { it.copy(message = message) }
        }
    }

    fun remind(task: StudyTask) {
        StudySyncNotifications.showLater(
            appContext,
            appContext.getString(R.string.notification_task_reminder_title),
            task.title
        )
        _state.update { it.copy(message = appContext.getString(R.string.planner_remind_scheduled)) }
    }

    companion object {
        fun factory(
            appContext: Context,
            offline: OfflineStudyRepository,
            repository: StudyRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlannerViewModel(appContext, offline, repository) as T
            }
        }
    }
}
