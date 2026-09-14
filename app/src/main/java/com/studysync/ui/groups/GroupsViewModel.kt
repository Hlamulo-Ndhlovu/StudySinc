package com.studysync.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studysync.data.StudyGroup
import com.studysync.data.StudyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupsUiState(
    val groups: List<StudyGroup> = emptyList(),
    val isLoading: Boolean = false,
    val showCreateDialog: Boolean = false,
    val newGroupName: String = "",
    val newGroupSubject: String = "",
    val error: String? = null
)

class GroupsViewModel(
    private val repository: StudyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GroupsUiState())
    val state: StateFlow<GroupsUiState> = _state

    init {
        viewModelScope.launch {
            repository.observeGroups()
                .catch { throwable -> _state.update { it.copy(error = throwable.message) } }
                .collect { groups ->
                    _state.update { it.copy(groups = groups, isLoading = false) }
                }
        }
    }

    fun setDialogOpen(open: Boolean) = _state.update { it.copy(showCreateDialog = open, error = null) }

    fun onGroupNameChanged(value: String) = _state.update { it.copy(newGroupName = value) }

    fun onGroupSubjectChanged(value: String) = _state.update { it.copy(newGroupSubject = value) }

    fun createGroup() {
        val name = _state.value.newGroupName.trim()
        if (name.isBlank()) {
            _state.update { it.copy(error = "Group name required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = repository.createGroup(name, _state.value.newGroupSubject)
            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            showCreateDialog = false,
                            newGroupName = "",
                            newGroupSubject = ""
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
                    return GroupsViewModel(repository) as T
                }
            }
    }
}
