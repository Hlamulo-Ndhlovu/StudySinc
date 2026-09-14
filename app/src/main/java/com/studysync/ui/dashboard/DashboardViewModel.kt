package com.studysync.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.studysync.AppSettings
import com.studysync.data.ClassSession
import com.studysync.data.Message
import com.studysync.data.StudyGroup
import com.studysync.data.StudyRepository
import com.studysync.gamification.Gamification
import com.studysync.gamification.GamificationProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val userName: String = "Member",
    val nextClass: ClassSession? = null,
    val totalClasses: Int = 0,
    val totalFriends: Int = 0,
    val totalGroups: Int = 0,
    val onlineFriendsCount: Int = 0,
    val activeGroupsCount: Int = 0,
    val nowMillis: Long = System.currentTimeMillis(),
    val recentMessages: List<Message> = emptyList(),
    val spotlightGroups: List<StudyGroup> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val gamification: GamificationProgress = Gamification.from(
        com.studysync.data.DashboardSnapshot(),
        0
    )
)

class DashboardViewModel(
    private val repository: StudyRepository,
    private val settings: AppSettings
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state

    init {
        viewModelScope.launch {
            settings.noteDailyActivity()
            combine(
                repository.observeDashboard(),
                repository.currentUser,
                settings.streakDays
            ) { snapshot, user, streak ->
                Triple(
                    snapshot,
                    user?.displayName.orEmpty().ifBlank { user?.email?.substringBefore("@").orEmpty() },
                    streak
                )
            }
                .catch { throwable -> _state.update { it.copy(isLoading = false, error = throwable.message) } }
                .collect { (snapshot, name, streak) ->
                    _state.update {
                        it.copy(
                            userName = name.ifBlank { "Member" },
                            nextClass = snapshot.nextClass,
                            totalClasses = snapshot.totalClasses,
                            totalFriends = snapshot.totalFriends,
                            totalGroups = snapshot.totalGroups,
                            onlineFriendsCount = snapshot.onlineFriendsCount,
                            activeGroupsCount = snapshot.activeGroupsCount,
                            nowMillis = snapshot.nowMillis,
                            recentMessages = snapshot.recentMessages,
                            spotlightGroups = snapshot.spotlightGroups,
                            isLoading = false,
                            error = null,
                            gamification = Gamification.from(snapshot, streak)
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(repository: StudyRepository, settings: AppSettings): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DashboardViewModel(repository, settings) as T
                }
            }
    }
}
