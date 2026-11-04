package com.studysync.ui.resources

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studysync.R
import com.studysync.data.Resource
import com.studysync.data.StudyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    fun uploadFromPicker(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val name = displayName(resolver, uri)
                val type = resolver.getType(uri)
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext Result.failure(
                        IllegalStateException(context.getString(R.string.resource_read_failed))
                    )
                if (bytes.size > MAX_BYTES) {
                    return@withContext Result.failure(
                        IllegalStateException(context.getString(R.string.resource_too_large))
                    )
                }
                repository.uploadResource(groupId, name, bytes.size.toLong(), bytes, type)
            }
            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            lastDownloadMessage = context.getString(R.string.resource_uploaded)
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update { it.copy(isLoading = false, error = throwable.message) }
                }
            )
        }
    }

    fun download(context: Context, resource: Resource) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, lastDownloadMessage = null, error = null) }
            val result = repository.downloadResource(groupId, resource.id)
            result.fold(
                onSuccess = { saved ->
                    val url = saved.downloadUrl.ifBlank { resource.downloadUrl }
                    if (!url.startsWith("http")) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = context.getString(R.string.resource_blob_missing)
                            )
                        }
                        return@fold
                    }
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        _state.update {
                            it.copy(
                                isLoading = false,
                                lastDownloadMessage = context.getString(R.string.resource_opened, saved.name)
                            )
                        }
                    } catch (_: ActivityNotFoundException) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = context.getString(R.string.resource_open_failed)
                            )
                        }
                    }
                },
                onFailure = { throwable ->
                    _state.update { it.copy(isLoading = false, error = throwable.message) }
                }
            )
        }
    }

    private fun displayName(resolver: android.content.ContentResolver, uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    val name = cursor.getString(index)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "Resource"
    }

    companion object {
        private const val MAX_BYTES = 8 * 1024 * 1024

        fun factory(groupId: String, repository: StudyRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ResourcesViewModel(groupId, repository) as T
                }
            }
    }
}
