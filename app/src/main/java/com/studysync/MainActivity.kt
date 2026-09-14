package com.studysync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studysync.notifications.StudySyncNotifications
import com.studysync.ui.theme.StudySyncTheme

class MainActivity : ComponentActivity() {

    private var pendingInviteUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Graph.settings.attach(this)
        StudySyncNotifications.ensureChannels(this)
        pendingInviteUri = intent?.data
        setContent {
            val useDark = Graph.settings.useDarkTheme.collectAsStateWithLifecycle().value
            StudySyncTheme(useDarkTheme = useDark) {
                StudySyncApp(
                    repository = Graph.repository,
                    settings = Graph.settings,
                    pendingInviteUri = pendingInviteUri,
                    onConsumePendingInvite = { pendingInviteUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingInviteUri = intent?.data
    }
}
