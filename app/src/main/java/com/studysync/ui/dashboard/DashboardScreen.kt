package com.studysync.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studysync.R
import com.studysync.ui.common.StudySyncDashboardTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onOpenClasses: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenChats: () -> Unit,
    onOpenMenu: () -> Unit,
    appNotificationsEnabled: Boolean,
    onOpenAppNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
    onViewAllQuickActions: () -> Unit,
    onOpenProgress: () -> Unit
) {
    if (state.isLoading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.dashboard_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val userInitial = state.userName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val activityItems = buildActivityLines(state)
    val context = LocalContext.current
    val systemPostsNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    val showNotificationAttentionDot = !appNotificationsEnabled || !systemPostsNotifications

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudySyncDashboardTopBar(
                onMenuClick = onOpenMenu,
                onNotificationsClick = onOpenAppNotifications,
                onProfileClick = onOpenProfile,
                userInitial = userInitial,
                appName = stringResource(R.string.app_name),
                showNotificationDot = showNotificationAttentionDot
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.dashboard_greeting, state.userName),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.dashboard_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { StatsRow(state) }

            item {
                GamificationCard(progress = state.gamification, onOpen = onOpenProgress)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.section_quick_actions),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = onViewAllQuickActions) {
                        Text(
                            text = stringResource(R.string.dashboard_view_all),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item {
                QuickActionsRow(
                    onNewSession = onOpenClasses,
                    onJoinGroup = onOpenGroups,
                    onFlashcards = onOpenGroups,
                    onAddNote = onOpenChats
                )
            }

            item {
                Text(
                    text = stringResource(R.string.section_recent_activity),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (activityItems.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.dashboard_empty_activity),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(activityItems) { line ->
                    ActivityLineCard(line)
                }
            }

            if (state.error != null) {
                item {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun buildActivityLines(state: DashboardUiState): List<String> {
    val now = state.nowMillis
    val lines = mutableListOf<String>()
    state.spotlightGroups.take(2).forEach { g ->
        val preview = g.lastMessagePreview?.takeIf { it.isNotBlank() } ?: "New activity"
        val time = g.lastMessageAt?.let { formatRelative(now - it) }.orEmpty()
        val suffix = if (time.isNotBlank()) " ($time)" else ""
        lines += "${g.name} — $preview$suffix"
    }
    state.nextClass?.let { c ->
        val tail = when {
            c.isLive -> "Live now"
            else -> "Starts ${c.startTimeMillis.toPrettyTime()}"
        }
        lines.add(0, "${c.title} — $tail")
    }
    state.recentMessages.take(3).forEach { m ->
        val time = formatRelative(now - m.timestamp)
        val snippet = m.content.trim().replace("\n", " ").let { t ->
            if (t.length > 48) t.take(48) + "…" else t
        }
        lines += "Message from ${m.senderName} — $snippet ($time)"
    }
    return lines.distinct().take(6)
}

@Composable
private fun GamificationCard(
    progress: com.studysync.gamification.GamificationProgress,
    onOpen: () -> Unit
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.gamification_level, progress.level),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.gamification_streak_days, progress.streakDays),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LinearProgressIndicator(
                progress = progress.progressFraction,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.gamification_dashboard_cta),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActivityLineCard(line: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatsRow(state: DashboardUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = stringResource(R.string.stat_classes),
            value = state.totalClasses.toString(),
            icon = Icons.Default.School,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = stringResource(R.string.stat_friends),
            value = state.totalFriends.toString(),
            icon = Icons.Default.PersonAdd,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = stringResource(R.string.stat_groups),
            value = state.totalGroups.toString(),
            icon = Icons.Default.Group,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickActionsRow(
    onNewSession: () -> Unit,
    onJoinGroup: () -> Unit,
    onFlashcards: () -> Unit,
    onAddNote: () -> Unit
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        QuickCircleAction(
            label = stringResource(R.string.quick_new_session),
            icon = Icons.Default.FlashOn,
            onClick = onNewSession
        )
        QuickCircleAction(
            label = stringResource(R.string.quick_join_group),
            icon = Icons.Default.Group,
            onClick = onJoinGroup
        )
        QuickCircleAction(
            label = stringResource(R.string.quick_flashcards),
            icon = Icons.Default.Folder,
            onClick = onFlashcards
        )
        QuickCircleAction(
            label = stringResource(R.string.quick_add_note),
            icon = Icons.Default.Create,
            onClick = onAddNote
        )
    }
}

@Composable
private fun QuickCircleAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatRelative(deltaMs: Long): String {
    if (deltaMs < 0) return ""
    val minutes = max(1, deltaMs / 60_000L)
    return when {
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1440}d ago"
    }
}

private fun Long.toPrettyTime(): String {
    val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return formatter.format(Date(this))
}
