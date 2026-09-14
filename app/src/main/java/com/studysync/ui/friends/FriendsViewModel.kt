package com.studysync.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studysync.data.Friend
import com.studysync.data.StudyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
    val friends: List<Friend> = emptyList(),
    val isLoading: Boolean = false,
    val showAddDialog: Boolean = false,
    val newFriendName: String = "",
    val newFriendEmail: String = "",
    val error: String? = null
)

class FriendsViewModel(
    private val repository: StudyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FriendsUiState(isLoading = true))
    val state: StateFlow<FriendsUiState> = _state

    init {
        viewModelScope.launch {
            repository.observeFriends()
                .catch { throwable -> _state.update { it.copy(error = throwable.message, isLoading = false) } }
                .collect { friends ->
                    _state.update { it.copy(friends = friends, isLoading = false, error = null) }
                }
        }
    }

    fun setDialog(open: Boolean) = _state.update { it.copy(showAddDialog = open, error = null) }

    fun onNameChange(value: String) = _state.update { it.copy(newFriendName = value) }

    fun onEmailChange(value: String) = _state.update { it.copy(newFriendEmail = value) }

    fun addFriend() {
        val email = _state.value.newFriendEmail.trim()
        if (email.isBlank()) {
            _state.update { it.copy(error = "Email required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = repository.addFriend(_state.value.newFriendName, email)
            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            showAddDialog = false,
                            newFriendName = "",
                            newFriendEmail = "",
                            error = null
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
                    return FriendsViewModel(repository) as T
                }
            }
    }
}
