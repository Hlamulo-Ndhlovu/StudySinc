package com.studysync.ui.classes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.School
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.studysync.R
import com.studysync.ui.common.StudySyncHeroBanner
import com.studysync.ui.common.StudySyncSimplePrimaryTopBar
import com.studysync.data.ClassSession
import com.studysync.notifications.StudySyncNotifications
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassesScreen(
    state: ClassesUiState,
    notificationsEnabled: Boolean,
    onCreateClass: () -> Unit,
    onTitleChange: (String) -> Unit,
    onTopicChange: (String) -> Unit,
    onStartChange: (Long) -> Unit,
    onDurationChange: (Int) -> Unit,
    onMeetingLinkChange: (String) -> Unit,
    onLiveToggle: (Boolean) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onInstructorChange: (String) -> Unit,
    onToggleDialog: (Boolean) -> Unit,
    onClassSelected: (ClassSession) -> Unit,
    onNavigateToCreatedClass: (String) -> Unit,
    onConsumedNavigateToCreatedClass: () -> Unit,
    onConsumedClassCreatedEvent: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val pendingNotificationAfterPermission = remember { mutableStateOf<ClassCreatedEvent?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val ev = pendingNotificationAfterPermission.value
        pendingNotificationAfterPermission.value = null
        if (granted && ev != null && notificationsEnabled) {
            StudySyncNotifications.showClassCreated(context.applicationContext, ev.title, ev.classId)
        }
    }

    LaunchedEffect(state.navigateToClassId, state.classCreatedEvent) {
        val id = state.navigateToClassId ?: return@LaunchedEffect
        onNavigateToCreatedClass(id)
        onConsumedNavigateToCreatedClass()
        val ev = state.classCreatedEvent
        if (ev != null && ev.classId == id) {
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.class_created_snackbar, ev.title)
            )
            if (notificationsEnabled) {
                val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                if (canPost) {
                    StudySyncNotifications.showClassCreated(context.applicationContext, ev.title, ev.classId)
                } else {
                    pendingNotificationAfterPermission.value = ev
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            onConsumedClassCreatedEvent()
        } else if (ev != null) {
            onConsumedClassCreatedEvent()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            StudySyncSimplePrimaryTopBar(
                title = stringResource(R.string.nav_classes),
                onMenuClick = onOpenMenu
            )
        },
        floatingActionButton = {
            if (state.classes.isEmpty()) {
                FloatingActionButton(
                    onClick = { onToggleDialog(true) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create class")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                StudySyncHeroBanner(
                    title = stringResource(R.string.nav_classes),
                    subtitle = stringResource(R.string.classes_hero_subtitle)
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
            if (state.classes.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.classes_hint_has_items),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.classes.isEmpty() && state.error == null) {
                item {
                    Text(
                        text = stringResource(R.string.classes_hint_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state.classes) { session ->
                ClassRow(session, onClick = { onClassSelected(session) })
            }
        }

        if (state.showDialog) {
            CreateClassDialog(
                title = state.title,
                topic = state.topic,
                startTime = state.startTimeMillis,
                durationMinutes = state.durationMinutes,
                location = state.location,
                instructor = state.instructor,
                meetingLink = state.meetingLink,
                isLive = state.isLive,
                description = state.description,
                onTitleChange = onTitleChange,
                onTopicChange = onTopicChange,
                onStartChange = onStartChange,
                onDurationChange = onDurationChange,
                onLocationChange = onLocationChange,
                onInstructorChange = onInstructorChange,
                onMeetingLinkChange = onMeetingLinkChange,
                onLiveToggle = onLiveToggle,
                onDescriptionChange = onDescriptionChange,
                isBusy = state.isLoading,
                onConfirm = onCreateClass,
                onDismiss = { onToggleDialog(false) }
            )
        }
    }
}

@Composable
private fun ClassRow(session: ClassSession, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    modifier = Modifier.padding(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(session.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = session.topic,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${session.startTimeMillis.formatDate()} • ${session.durationMinutes} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (session.isLive) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                "LIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = "${session.hostName} • ${session.attendeeCount} listed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateClassDialog(
    isBusy: Boolean,
    title: String,
    topic: String,
    startTime: Long,
    durationMinutes: Int,
    location: String,
    instructor: String,
    meetingLink: String,
    isLive: Boolean,
    description: String,
    onTitleChange: (String) -> Unit,
    onTopicChange: (String) -> Unit,
    onStartChange: (Long) -> Unit,
    onDurationChange: (Int) -> Unit,
    onLocationChange: (String) -> Unit,
    onInstructorChange: (String) -> Unit,
    onMeetingLinkChange: (String) -> Unit,
    onLiveToggle: (Boolean) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !isBusy) {
                Text(if (isBusy) "Creating…" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Cancel") }
        },
        title = { Text("Create online class") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = onTopicChange,
                    label = { Text("Topic") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Starts: ${SimpleDateFormat("EEE, MMM d • HH:mm", Locale.getDefault()).format(Date(startTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = { onStartChange(startTime - 15 * 60 * 1000) }) { Text("-15 min") }
                    TextButton(onClick = { onStartChange(startTime + 15 * 60 * 1000) }) { Text("+15 min") }
                }
                OutlinedTextField(
                    value = durationMinutes.toString(),
                    onValueChange = { value -> value.toIntOrNull()?.let(onDurationChange) },
                    label = { Text("Duration (min)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = onLocationChange,
                    label = { Text("Location / Link") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = instructor,
                    onValueChange = onInstructorChange,
                    label = { Text("Instructor") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description / Agenda") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = meetingLink,
                    onValueChange = onMeetingLinkChange,
                    label = { Text("Meeting link") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mark as live", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.material3.Switch(
                        checked = isLive,
                        onCheckedChange = onLiveToggle
                    )
                }
            }
        }
    )
}

private fun Long.formatDate(): String {
    val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return formatter.format(Date(this))
}
