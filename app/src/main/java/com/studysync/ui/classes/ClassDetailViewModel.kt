package com.studysync.ui.classes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studysync.data.ClassSession
import com.studysync.data.Resource
import com.studysync.data.StudyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClassDetailUiState(
    val session: ClassSession? = null,
    val notes: List<Resource> = emptyList(),
    val currentUserId: String? = null,
    val nowMillis: Long = System.currentTimeMillis(),
    /** Signed-in users with the in-app live meeting open (Firestore snapshot). */
    val liveInCallCount: Int = 0,
    val isUpdating: Boolean = false,
    val isDeleting: Boolean = false,
    val isUploadingNote: Boolean = false,
    val pendingNavigateBackAfterDelete: Boolean = false,
    val error: String? = null
)

class ClassDetailViewModel(
    private val classId: String,
    private val repository: StudyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ClassDetailUiState())
    val state: StateFlow<ClassDetailUiState> = _state

    init {
        viewModelScope.launch {
            combine(
                repository.observeClass(classId),
                repository.observeClassNotes(classId),
                repository.currentUser,
                repository.observeServerClock(),
                repository.observeLiveClassPresence(classId)
            ) { session, notes, user, now, liveCount ->
                _state.value.copy(
                    session = session,
                    notes = notes,
                    currentUserId = user?.id,
                    nowMillis = now,
                    liveInCallCount = liveCount
                )
            }
                .catch { throwable -> _state.update { it.copy(error = throwable.message) } }
                .collect { merged ->
                    _state.value = merged
                }
        }
    }

    fun toggleLive() {
        val session = _state.value.session ?: return
        val uid = _state.value.currentUserId ?: return
        if (session.ownerUid.isNotBlank() && session.ownerUid != uid) return
        viewModelScope.launch {
            _state.update { it.copy(isUpdating = true, error = null) }
            val result = repository.updateClassLive(classId, !session.isLive)
            result.fold(
                onSuccess = { _state.update { it.copy(isUpdating = false) } },
                onFailure = { throwable -> _state.update { it.copy(isUpdating = false, error = throwable.message) } }
            )
        }
    }

    fun deleteClass() {
        viewModelScope.launch {
            _state.update { it.copy(isDeleting = true, error = null) }
            val result = repository.deleteClass(classId)
            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(isDeleting = false, pendingNavigateBackAfterDelete = true)
                    }
                },
                onFailure = { throwable ->
                    _state.update { it.copy(isDeleting = false, error = throwable.message) }
                }
            )
        }
    }

    fun consumedDeleteNavigation() {
        _state.update { it.copy(pendingNavigateBackAfterDelete = false) }
    }

    fun uploadClassNote(name: String, sizeBytes: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isUploadingNote = true, error = null) }
            val result = repository.uploadClassNote(classId, name, sizeBytes)
            result.fold(
                onSuccess = { _state.update { it.copy(isUploadingNote = false) } },
                onFailure = { throwable ->
                    _state.update { it.copy(isUploadingNote = false, error = throwable.message) }
                }
            )
        }
    }

    fun deleteClassNote(noteId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isUpdating = true, error = null) }
            val result = repository.deleteClassNote(classId, noteId)
            result.fold(
                onSuccess = { _state.update { it.copy(isUpdating = false) } },
                onFailure = { throwable ->
                    _state.update { it.copy(isUpdating = false, error = throwable.message) }
                }
            )
        }
    }

    companion object {
        fun factory(classId: String, repository: StudyRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ClassDetailViewModel(classId, repository) as T
                }
            }
    }
}
