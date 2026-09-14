package com.studysync.ui.classes

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studysync.data.ClassSession
import com.studysync.data.Resource
import com.studysync.ui.common.SectionLabel
import com.studysync.ui.common.StudySyncHeroBanner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailScreen(
    state: ClassDetailUiState,
    onBack: () -> Unit,
    onToggleLive: () -> Unit,
    onJoinCall: () -> Unit,
    onDeleteClass: () -> Unit,
    onUploadNote: (name: String, sizeBytes: Long) -> Unit,
    onDeleteNote: (noteId: String) -> Unit
) {
    val context = LocalContext.current
    val session = state.session
    var showDeleteDialog by remember { mutableStateOf(false) }

    val notePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        var name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "note"
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) {
                    val n = c.getString(idx)
                    if (!n.isNullOrBlank()) name = n
                }
            }
        }
        val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        onUploadNote(name, size.coerceAtLeast(1L))
    }

    val uid = state.currentUserId
    val isHost = session != null && uid != null &&
        (session.ownerUid.isBlank() || session.ownerUid == uid)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Class") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (session != null) {
                        IconButton(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, ClassInviteLinks.shareMessage(session))
                                }
                                context.startActivity(Intent.createChooser(send, "Share class invite"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share invite link")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (session == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = state.error ?: "Loading...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.error?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            StudySyncHeroBanner(
                title = session.title,
                subtitle = "${session.topic} • ${session.durationMinutes} min • ${session.startTimeMillis.toPrettyDateTime()}"
            )

            if (session.isLive) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = "LIVE NOW — Join the in-app meeting or share the link. ${state.liveInCallCount} in app.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Text(
                buildString {
                    append("Host: ${session.hostName}")
                    if (state.liveInCallCount > 0) {
                        append(" • In app: ${state.liveInCallCount}")
                    }
                    append(" • Listed: ${session.attendeeCount}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!session.isLive && session.startTimeMillis > state.nowMillis) {
                Text(
                    text = "Starts in ${formatDuration(session.startTimeMillis - state.nowMillis)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (session.description.isNotBlank()) {
                Text(
                    session.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }

            SectionLabel("Meeting")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onJoinCall,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (session.isLive) "Join live" else "Meeting")
                        }
                        if (isHost) {
                            Button(
                                onClick = onToggleLive,
                                modifier = Modifier.weight(1f),
                                enabled = !state.isUpdating
                            ) {
                                Text(if (session.isLive) "End live" else "Go live")
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(session.meetingLink.ifBlank { "https://meet.studysync.app" })
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Browser link")
                    }
                    if (isHost) {
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isDeleting
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete class")
                        }
                    }
                }
            }

            SectionLabel("Notes & files")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Class notes", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Upload PDFs or other files for everyone in this class.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { notePicker.launch("*/*") },
                        enabled = !state.isUploadingNote && uid != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (state.isUploadingNote) "Uploading…" else "Upload note")
                    }
                    if (state.notes.isEmpty()) {
                        Text(
                            "No notes yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.notes.forEach { note ->
                            NoteRow(
                                note = note,
                                currentUserId = uid,
                                classOwnerUid = session.ownerUid,
                                isHost = isHost,
                                onDelete = { onDeleteNote(note.id) }
                            )
                        }
                    }
                }
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete class?") },
                text = { Text("This removes the class and all uploaded notes for everyone. This can’t be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            onDeleteClass()
                        },
                        enabled = !state.isDeleting
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun NoteRow(
    note: Resource,
    currentUserId: String?,
    classOwnerUid: String,
    isHost: Boolean,
    onDelete: () -> Unit
) {
    val canDelete = currentUserId != null && (
        note.ownerUid == currentUserId ||
            (classOwnerUid.isNotBlank() && classOwnerUid == currentUserId) ||
            (classOwnerUid.isBlank() && isHost)
        )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(note.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${note.ownerName} • ${formatFileSize(note.sizeBytes)} • ${note.uploadedAt.toRelativeTime()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (canDelete) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove note")
            }
        }
    }
}

private fun Long.toPrettyDateTime(): String {
    val fmt = SimpleDateFormat("EEE, MMM d h:mm a", Locale.getDefault())
    return fmt.format(Date(this))
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0s"
    val secs = ms / 1000L
    val m = secs / 60
    val s = secs % 60
    val h = m / 60
    val min = m % 60
    return if (h > 0L) "${h}h ${min}m" else if (m > 0L) "${m}m ${s}s" else "${s}s"
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.getDefault(), "%.1f MB", mb)
}

private fun Long.toRelativeTime(): String {
    val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return fmt.format(Date(this))
}
