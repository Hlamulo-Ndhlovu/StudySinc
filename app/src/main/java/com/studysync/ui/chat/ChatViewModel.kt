package com.studysync.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studysync.data.Message
import com.studysync.data.StudyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val groupId: String,
    val groupName: String = "Chat",
    val subject: String = "",
    val memberCount: Int? = null,
    val messages: List<Message> = emptyList(),
    val input: String = "",
    val error: String? = null,
    val isSending: Boolean = false,
    val currentUserId: String? = null,
    val typingLabel: String? = null
)

class ChatViewModel(
    private val groupId: String,
    private val repository: StudyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState(groupId))
    val state: StateFlow<ChatUiState> = _state
    private var typingJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeTyping(groupId)
                .catch { /* ignore */ }
                .collect { label -> _state.update { it.copy(typingLabel = label) } }
        }
        viewModelScope.launch {
            repository.observeMessages(groupId)
                .catch { throwable -> _state.update { it.copy(error = throwable.message) } }
                .collect { messages ->
                    _state.update { it.copy(messages = messages, isSending = false) }
                }
        }
        viewModelScope.launch {
            repository.observeGroup(groupId)
                .catch { throwable -> _state.update { it.copy(error = throwable.message) } }
                .collect { group ->
                    _state.update {
                        if (group == null) {
                            it.copy(error = "Group not found")
                        } else {
                            it.copy(
                                groupName = group.name,
                                subject = group.subject,
                                memberCount = group.memberCount,
                                error = null
                            )
                        }
                    }
                }
        }
        viewModelScope.launch {
            repository.currentUser.collect { user ->
                _state.update { it.copy(currentUserId = user?.id) }
            }
        }
    }

    fun onMessageChanged(value: String) {
        _state.update { it.copy(input = value) }
        typingJob?.cancel()
        if (value.isBlank()) {
            viewModelScope.launch { repository.reportTyping(groupId, false) }
            return
        }
        typingJob = viewModelScope.launch {
            delay(380L)
            repository.reportTyping(groupId, true)
        }
    }

    fun sendMessage() {
        val text = _state.value.input.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            typingJob?.cancel()
            repository.reportTyping(groupId, false)
            _state.update { it.copy(isSending = true, error = null) }
            val result = repository.sendMessage(groupId, text)
            result.fold(
                onSuccess = { _state.update { it.copy(input = "", isSending = false, typingLabel = null) } },
                onFailure = { throwable ->
                    _state.update { it.copy(isSending = false, error = throwable.message) }
                }
            )
        }
    }
}

class ChatViewModelFactory(
    private val groupId: String,
    private val repository: StudyRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(groupId, repository) as T
    }
}
