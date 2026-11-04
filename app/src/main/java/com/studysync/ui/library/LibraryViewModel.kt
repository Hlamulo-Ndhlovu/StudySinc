package com.studysync.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studysync.data.LibraryBook
import com.studysync.data.remote.LibraryRepository
import com.studysync.data.sync.OfflineStudyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val query: String = "",
    val books: List<LibraryBook> = emptyList(),
    val loading: Boolean = false,
    val fromCache: Boolean = false,
    val online: Boolean = true,
    val error: String? = null,
    val searched: Boolean = false
)

class LibraryViewModel(
    private val library: LibraryRepository,
    private val offline: OfflineStudyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state

    init {
        viewModelScope.launch {
            offline.online.collect { online -> _state.update { it.copy(online = online) } }
        }
    }

    fun onQuery(value: String) = _state.update { it.copy(query = value) }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = library.search(query, online = offline.online.value)
            _state.update {
                it.copy(
                    loading = false,
                    books = result.books,
                    fromCache = result.fromCache,
                    error = result.error,
                    searched = true
                )
            }
        }
    }

    companion object {
        fun factory(
            library: LibraryRepository,
            offline: OfflineStudyRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LibraryViewModel(library, offline) as T
            }
        }
    }
}
