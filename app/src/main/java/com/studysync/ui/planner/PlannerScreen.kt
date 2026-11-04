package com.studysync.ui.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.studysync.R
import com.studysync.data.StudyTask
import com.studysync.ui.common.SectionLabel
import com.studysync.ui.common.StudySyncHeroBanner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    state: PlannerUiState,
    onBack: () -> Unit,
    onSync: () -> Unit,
    onOpenDialog: () -> Unit,
    onDismissDialog: () -> Unit,
    onTitle: (String) -> Unit,
    onSubject: (String) -> Unit,
    onNotes: (String) -> Unit,
    onPriority: (Int) -> Unit,
    onSave: () -> Unit,
    onDone: (StudyTask, Boolean) -> Unit,
    onDelete: (StudyTask) -> Unit,
    onRemind: (StudyTask) -> Unit,
    onClearMessage: () -> Unit
) {
    val pending = state.tasks.count { it.pendingSync }
    val status = when {
        !state.online -> stringResource(R.string.planner_status_offline, pending)
        pending > 0 -> stringResource(R.string.planner_status_online_pending, pending)
        else -> stringResource(R.string.planner_status_synced)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.planner_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenDialog) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.planner_add))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StudySyncHeroBanner(
                title = stringResource(R.string.planner_hero_title),
                subtitle = stringResource(R.string.planner_hero_subtitle)
            )
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.online) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(status, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = onSync,
                        enabled = !state.syncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (state.syncing) stringResource(R.string.planner_syncing)
                            else stringResource(R.string.planner_sync_now)
                        )
                    }
                }
            }
            if (state.message != null) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onClearMessage) { Text(stringResource(R.string.action_dismiss)) }
            }
            SectionLabel(stringResource(R.string.planner_section_tasks))
            if (state.tasks.isEmpty()) {
                Text(
                    text = stringResource(R.string.planner_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.tasks.forEach { task ->
                    TaskCard(task, onDone, onDelete, onRemind)
                }
            }
        }
    }

    if (state.dialogOpen) {
        AlertDialog(
            onDismissRequest = onDismissDialog,
            title = { Text(stringResource(R.string.planner_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.draftTitle,
                        onValueChange = onTitle,
                        label = { Text(stringResource(R.string.planner_title_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.draftSubject,
                        onValueChange = onSubject,
                        label = { Text(stringResource(R.string.planner_subject_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.draftNotes,
                        onValueChange = onNotes,
                        label = { Text(stringResource(R.string.planner_notes_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.planner_priority), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PriorityChip(0, R.string.planner_priority_low, state.draftPriority, onPriority)
                        PriorityChip(1, R.string.planner_priority_medium, state.draftPriority, onPriority)
                        PriorityChip(2, R.string.planner_priority_high, state.draftPriority, onPriority)
                    }
                }
            },
            confirmButton = {
                Button(onClick = onSave) { Text(stringResource(R.string.planner_save)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissDialog) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun PriorityChip(value: Int, labelRes: Int, selected: Int, onPriority: (Int) -> Unit) {
    if (selected == value) {
        Button(onClick = { onPriority(value) }) { Text(stringResource(labelRes)) }
    } else {
        OutlinedButton(onClick = { onPriority(value) }) { Text(stringResource(labelRes)) }
    }
}

@Composable
private fun TaskCard(
    task: StudyTask,
    onDone: (StudyTask, Boolean) -> Unit,
    onDelete: (StudyTask) -> Unit,
    onRemind: (StudyTask) -> Unit
) {
    val due = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(task.dueAt))
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = task.done, onCheckedChange = { onDone(task, it) })
                Column(Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
                    )
                    Text(
                        text = task.subject,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = stringResource(R.string.planner_due, due),
                style = MaterialTheme.typography.bodySmall
            )
            if (task.notes.isNotBlank()) {
                Text(task.notes, style = MaterialTheme.typography.bodyMedium)
            }
            if (task.pendingSync) {
                Text(
                    text = stringResource(R.string.planner_pending),
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Row {
                TextButton(onClick = { onRemind(task) }) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Text(stringResource(R.string.planner_remind), modifier = Modifier.padding(start = 4.dp))
                }
                IconButton(onClick = { onDelete(task) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.planner_delete))
                }
            }
        }
    }
}
