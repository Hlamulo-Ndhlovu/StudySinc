package com.studysync.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    state: GroupsUiState,
    onCreateGroup: () -> Unit,
    onGroupSelected: (StudyGroup) -> Unit,
    onNewGroupNameChange: (String) -> Unit,
    onNewGroupSubjectChange: (String) -> Unit,
    onToggleDialog: (Boolean) -> Unit,
    onOpenMenu: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            StudySyncSimplePrimaryTopBar(
                title = "Groups",
                onMenuClick = onOpenMenu
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onToggleDialog(true) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create group")
            }
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
                    title = stringResource(R.string.groups_hero_title),
                    subtitle = stringResource(R.string.groups_hero_subtitle)
                )
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
            if (state.groups.isEmpty() && state.error == null) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.groups_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            }
            items(state.groups) { group ->
                GroupRow(group = group, onClick = { onGroupSelected(group) })
            }
        }

        if (state.showCreateDialog) {
            CreateGroupDialog(
                name = state.newGroupName,
                subject = state.newGroupSubject,
                onNameChange = onNewGroupNameChange,
                onSubjectChange = onNewGroupSubjectChange,
                onConfirm = onCreateGroup,
                onDismiss = { onToggleDialog(false) }
            )
        }
    }
}

@Composable
private fun GroupRow(group: StudyGroup, onClick: () -> Unit) {
    val subtitle = buildString {
        append("${group.subject} • ${group.memberCount} members")
        val preview = group.lastMessagePreview?.takeIf { it.isNotBlank() }
        if (preview != null) {
            append("\n")
            append(preview)
        }
    }
    ListRowCard(
        onClick = onClick,
        leading = { IconAvatar(textForInitial = group.name) },
        title = group.name,
        subtitle = subtitle,
        trailing = {
            Icon(
                Icons.Default.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    )
}

@Composable
private fun CreateGroupDialog(
    name: String,
    subject: String,
    onNameChange: (String) -> Unit,
    onSubjectChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onConfirm) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text(stringResource(R.string.groups_dialog_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = onSubjectChange,
                    label = { Text("Subject") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}
