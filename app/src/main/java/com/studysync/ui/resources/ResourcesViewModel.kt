package com.studysync.ui.resources

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studysync.data.Resource
import com.studysync.data.StudyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class ResourcesUiState(
    val resources: List<Resource> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastDownloadMessage: String? = null
)

class ResourcesViewModel(
    private val groupId: String,
    private val repository: StudyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ResourcesUiState())
    val state: StateFlow<ResourcesUiState> = _state

    init {
        viewModelScope.launch {
            repository.observeResources(groupId)
                .catch { throwable -> _state.update { it.copy(error = throwable.message) } }
                .collect { resources ->
                    _state.update { it.copy(resources = resources, isLoading = false, error = null) }
                }
        }
    }

    fun uploadFromPicker(uri: Uri) {
        val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "Resource" } ?: "Resource"
        val size = Random.nextLong(100_000L, 1_000_000L)
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = repository.uploadResource(groupId, name, size)
            result.fold(
                onSuccess = { _state.update { it.copy(isLoading = false, error = null) } },
                onFailure = { throwable -> _state.update { it.copy(isLoading = false, error = throwable.message) } }
            )
        }
    }

    fun download(resource: Resource) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, lastDownloadMessage = null, error = null) }
            val result = repository.downloadResource(groupId, resource.id)
            result.fold(
                onSuccess = {
                    _state.update { it.copy(isLoading = false, lastDownloadMessage = "Downloaded ${resource.name}") }
                },
                onFailure = { throwable ->
                    _state.update { it.copy(isLoading = false, error = throwable.message) }
                }
            )
        }
    }

    companion object {
        fun factory(groupId: String, repository: StudyRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ResourcesViewModel(groupId, repository) as T
                }
            }
    }
}
