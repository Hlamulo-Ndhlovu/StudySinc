package com.studysync.ui.call

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.studysync.BuildConfig
import com.studysync.data.StudyRepository
import io.livekit.android.compose.local.RoomScope
import io.livekit.android.compose.state.rememberParticipants
import io.livekit.android.compose.state.rememberTracks
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room
import io.livekit.android.util.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation

@Composable
fun CallScreen(
    roomName: String,
    classId: String,
    repository: StudyRepository,
    onLeave: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var permissionsGranted by remember {
        mutableStateOf(activity != null && CallPermissionHelper.hasPermissions(activity))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionsGranted = grants.values.all { it }
    }

    LaunchedEffect(activity) {
        if (activity != null && !permissionsGranted) {
            permissionLauncher.launch(CallPermissionHelper.permissions)
        }
    }

    LaunchedEffect(classId) {
        if (classId.isNotBlank()) {
            repository.joinLiveClass(classId)
        }
        try {
            awaitCancellation()
        } finally {
            if (classId.isNotBlank()) {
                repository.leaveLiveClass(classId)
            }
        }
    }

    if (activity == null) {
        CenteredMessage("Unable to open call from this screen.")
        return
    }

    if (!permissionsGranted) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Camera and microphone are required for live class meetings.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { permissionLauncher.launch(CallPermissionHelper.permissions) }) {
                Text("Grant permissions")
            }
        }
        return
    }

    var token by remember { mutableStateOf<String?>(null) }
    var tokenError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(roomName) {
        tokenError = null
        token = null
        try {
            val t = CallTokenProvider.fetchToken(roomName)
            if (t.isBlank()) {
                tokenError =
                    "No LiveKit token. Add LIVEKIT_TOKEN to local.properties (project root), or serve tokens from your backend."
            } else if (LiveKitJwtHelper.isExpired(t)) {
                tokenError = liveKitTokenExpiredMessage()
            } else {
                token = t
            }
        } catch (e: Exception) {
            tokenError = e.message ?: "Could not load token"
        }
    }

    val serverUrl = BuildConfig.LIVEKIT_URL.trim()
    when {
        serverUrl.isEmpty() -> {
            LiveKitNotConfiguredMessage(onLeave = onLeave)
        }
        tokenError != null -> CenteredMessage(tokenError!!)
        token == null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        else -> {
            var connectError by remember { mutableStateOf<String?>(null) }
            RoomScope(
                url = serverUrl,
                token = token,
                audio = true,
                video = true,
                connect = true,
                onError = { _, e ->
                    connectError = liveKitConnectErrorMessage(e)
                }
            ) { room ->
                CallRoomContent(
                    room = room,
                    roomName = roomName,
                    connectError = connectError,
                    onLeave = onLeave
                )
            }
        }
    }
}

@Composable
private fun CallRoomContent(
    room: Room,
    roomName: String,
    connectError: String?,
    onLeave: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val roomState by room::state.flow.collectAsState(initial = Room.State.DISCONNECTED)
    val participants by rememberParticipants(passedRoom = room)
    val trackRefs by rememberTracks(passedRoom = room)

    val micOn by room.localParticipant::isMicrophoneEnabled.flow.collectAsState(initial = true)
    val camOn by room.localParticipant::isCameraEnabled.flow.collectAsState(initial = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live class") },
                navigationIcon = {
                    IconButton(onClick = onLeave) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Leave")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = "Room: $roomName",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                text = when (roomState) {
                    Room.State.CONNECTING, Room.State.RECONNECTING -> "Connecting…"
                    Room.State.CONNECTED -> "Connected · ${participants.size} in call"
                    Room.State.DISCONNECTED -> "Disconnected"
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            connectError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(trackRefs) { _, ref ->
                    VideoTrackView(
                        trackReference = ref,
                        room = room,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        scope.launch {
                            room.localParticipant.setMicrophoneEnabled(!micOn)
                        }
                    }
                ) {
                    Icon(
                        if (micOn) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Microphone"
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            room.localParticipant.setCameraEnabled(!camOn)
                        }
                    }
                ) {
                    Icon(
                        if (camOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = "Camera"
                    )
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        room.disconnect()
                        onLeave()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Leave call")
            }
        }
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Shown when LIVEKIT_URL is unset. Video calls are optional; chat/classes use Firebase without LiveKit.
 */
@Composable
private fun LiveKitNotConfiguredMessage(onLeave: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Video calls aren’t set up yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "You don’t need LiveKit for the rest of StudySync (chat, groups, classes metadata, etc.). " +
                "LiveKit is only for real-time camera/microphone meetings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Easiest way to try it: create a free project on LiveKit Cloud, copy the WebSocket URL " +
                "(wss://…livekit.cloud) into LIVEKIT_URL in local.properties, then generate a test access token " +
                "in the project dashboard and paste it into LIVEKIT_TOKEN. Rebuild the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Self-hosting later: run LiveKit on your own server and use that wss:// URL instead.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onLeave) {
            Text("Back")
        }
    }
}

private fun liveKitTokenExpiredMessage(): String =
    "Your LiveKit access token has expired. Open https://cloud.livekit.io, select the same project as your " +
        "WebSocket URL (LIVEKIT_URL in local.properties), generate a new participant token, update LIVEKIT_TOKEN, " +
        "Sync Gradle, and rebuild."

private fun liveKitConnectErrorMessage(e: Throwable?): String {
    val raw = e?.message.orEmpty()
    val lower = raw.lowercase()
    if ("401" in raw || "unauthorized" in lower || "region" in lower) {
        val url = BuildConfig.LIVEKIT_URL.trim().ifBlank { "(LIVEKIT_URL not set)" }
        return "LiveKit could not verify your access (401 — often labeled “region settings”). " +
            "The JWT must be issued from the same LiveKit Cloud project as your WebSocket URL. " +
            "Fix: in that project’s dashboard, generate a new token and set LIVEKIT_TOKEN; ensure LIVEKIT_URL is " +
            "exactly this project’s URL. Tokens from another project or expired keys cause this. Current URL: $url"
    }
    return raw.ifBlank { "Could not connect to the meeting server." }
}

private fun Context.findActivity(): ComponentActivity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
