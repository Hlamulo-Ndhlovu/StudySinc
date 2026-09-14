package com.studysync.ui.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.studysync.R
import com.studysync.data.StudyGroup
import com.studysync.ui.common.IconAvatar
import com.studysync.ui.common.ListRowCard
import com.studysync.ui.common.StudySyncHeroBanner
import com.studysync.ui.common.StudySyncSimplePrimaryTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    groups: List<StudyGroup>,
    onChatSelected: (StudyGroup) -> Unit,
    onOpenMenu: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudySyncSimplePrimaryTopBar(
                title = "Messages",
                onMenuClick = onOpenMenu
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StudySyncHeroBanner(
                    title = "Messages",
                    subtitle = stringResource(R.string.chats_hero_subtitle)
                )
            }
            if (groups.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Text(
                            text = "No chats yet. Create a group to share files and start messaging.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            }
            items(groups) { group ->
                ListRowCard(
                    onClick = { onChatSelected(group) },
                    leading = { IconAvatar(textForInitial = group.name) },
                    title = group.name,
                    subtitle = buildSubtitle(group),
                    trailing = {
                        Icon(
                            Icons.Default.Chat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
        }
    }
}

private fun buildSubtitle(group: StudyGroup): String {
    val meta = "${group.subject} • ${group.memberCount} members"
    val preview = group.lastMessagePreview?.takeIf { it.isNotBlank() }
    val time = group.lastMessageAt?.let { formatTime(it) }
    return when {
        preview != null && time != null -> "$meta\n$preview · $time"
        preview != null -> "$meta\n$preview"
        else -> meta
    }
}

private fun formatTime(millis: Long): String {
    val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return fmt.format(Date(millis))
}
