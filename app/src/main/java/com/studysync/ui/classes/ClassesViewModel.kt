package com.studysync.ui.classes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studysync.data.ClassSession
import com.studysync.data.StudyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ClassCreatedEvent(
    val classId: String,
    val title: String
)

data class ClassesUiState(
    val classes: List<ClassSession> = emptyList(),
    /** After creating a class, navigate to its detail screen (then clear via [consumedNavigateToCreatedClass]). */
    val navigateToClassId: String? = null,
    /** One-shot: show snackbar + optional system notification (cleared via [consumedClassCreatedEvent]). */
    val classCreatedEvent: ClassCreatedEvent? = null,
    val isLoading: Boolean = false,
    val showDialog: Boolean = false,
    val title: String = "",
    val topic: String = "",
    val location: String = "Online",
    val instructor: String = "",
    val startTimeMillis: Long = System.currentTimeMillis() + 60 * 60 * 1000,
    val durationMinutes: Int = 60,
    val meetingLink: String = "",
    val isLive: Boolean = false,
    val description: String = "",
    val error: String? = null
)

class ClassesViewModel(
    private val repository: StudyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ClassesUiState(isLoading = true))
    val state: StateFlow<ClassesUiState> = _state

    init {
        viewModelScope.launch {
            repository.observeClasses()
                .catch { throwable -> _state.update { it.copy(error = throwable.message, isLoading = false) } }
                .collect { classes ->
                    _state.update { it.copy(classes = classes, isLoading = false) }
                }
        }
    }

    fun setDialog(open: Boolean) {
        val s = _state.value
        if (open && s.classes.isNotEmpty()) {
            _state.update {
                it.copy(
                    error = "You already have a class. Open it below, or delete it on the class screen to create another."
                )
            }
            return
        }
        _state.update { it.copy(showDialog = open, error = null) }
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value) }
    fun onTopicChange(value: String) = _state.update { it.copy(topic = value) }
    fun onLocationChange(value: String) = _state.update { it.copy(location = value) }
    fun onInstructorChange(value: String) = _state.update { it.copy(instructor = value) }
    fun onDurationChange(value: Int) = _state.update { it.copy(durationMinutes = value) }
    fun onStartTimeChange(value: Long) = _state.update { it.copy(startTimeMillis = value) }
    fun onMeetingLinkChange(value: String) = _state.update { it.copy(meetingLink = value) }
    fun onLiveToggle(value: Boolean) = _state.update { it.copy(isLive = value) }
    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }

    fun consumedNavigateToCreatedClass() =
        _state.update { it.copy(navigateToClassId = null) }

    fun consumedClassCreatedEvent() =
        _state.update { it.copy(classCreatedEvent = null) }

    fun createClass() {
        val title = _state.value.title.trim()
        if (title.isBlank()) {
            _state.update { it.copy(error = "Title required") }
            return
        }
        if (_state.value.classes.isNotEmpty()) {
            _state.update {
                it.copy(
                    error = "You already have a class. Open it below, or delete it first to create another."
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = repository.createClass(
                title = title,
                topic = _state.value.topic,
                startTimeMillis = _state.value.startTimeMillis,
                durationMinutes = _state.value.durationMinutes,
                location = _state.value.location,
                instructor = _state.value.instructor,
                meetingLink = _state.value.meetingLink.ifBlank { "https://meet.studysync.app/${UUID.randomUUID()}" },
                isLive = _state.value.isLive,
                description = _state.value.description
            )
            result.fold(
                onSuccess = { session ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            showDialog = false,
                            title = "",
                            topic = "",
                            location = "Online",
                            instructor = "",
                            meetingLink = "",
                            isLive = false,
                            description = "",
                            error = null,
                            navigateToClassId = session.id,
                            classCreatedEvent = ClassCreatedEvent(
                                classId = session.id,
                                title = session.title
                            )
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update { it.copy(isLoading = false, error = throwable.message) }
                }
            )
        }
    }

    companion object {
        fun factory(repository: StudyRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ClassesViewModel(repository) as T
                }
            }
    }
}
