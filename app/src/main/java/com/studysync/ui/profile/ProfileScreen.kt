package com.studysync.ui.profile

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.studysync.R
import com.studysync.ui.common.StudySyncProfileTopBar
import com.studysync.ui.theme.FacebookCoverEnd
import com.studysync.ui.theme.FacebookCoverEndDark
import com.studysync.ui.theme.FacebookCoverStart
import com.studysync.ui.theme.FacebookCoverStartDark
import com.studysync.ui.theme.FacebookPageBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    initialDisplayName: String,
    initialFirstName: String,
    initialLastName: String,
    initialEmail: String,
    initialPhone: String,
    initialAvatarUrl: String,
    userId: String,
    friendsCount: Int,
    groupsCount: Int,
    onSave: (firstName: String, lastName: String, displayName: String, phone: String, avatarUrl: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMenu: () -> Unit,
    onSearchFriends: () -> Unit
) {
    var displayName by remember { mutableStateOf(initialDisplayName) }
    var firstName by remember { mutableStateOf(initialFirstName) }
    var lastName by remember { mutableStateOf(initialLastName) }
    var email by remember { mutableStateOf(initialEmail) }
    var phone by remember { mutableStateOf(initialPhone) }
    var avatarUrl by remember { mutableStateOf(initialAvatarUrl) }
    var isEditing by remember { mutableStateOf(false) }
    val defaultBio = stringResource(R.string.profile_default_bio)
    var bio by remember(defaultBio) { mutableStateOf(defaultBio) }
    var selectedTab by remember { mutableStateOf(0) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) avatarUrl = uri.toString()
    }

    LaunchedEffect(
        initialDisplayName,
        initialFirstName,
        initialLastName,
        initialEmail,
        initialPhone,
        initialAvatarUrl
    ) {
        displayName = initialDisplayName
        firstName = initialFirstName
        lastName = initialLastName
        email = initialEmail
        phone = initialPhone
        avatarUrl = initialAvatarUrl
    }

    val lightProfile = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val pageBg = if (lightProfile) FacebookPageBackground else MaterialTheme.colorScheme.background
    val coverBrush = Brush.horizontalGradient(
        if (lightProfile) {
            listOf(FacebookCoverStart, FacebookCoverEnd)
        } else {
            listOf(FacebookCoverStartDark, FacebookCoverEndDark)
        }
    )

    val displayInitials = remember(displayName, firstName, lastName) {
        val parts = listOf(firstName, lastName).filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            displayName.isNotBlank() -> displayName.trim().take(2).uppercase()
            else -> "?"
        }
    }

    val studentId = remember(userId) {
        val tail = userId.filter { it.isLetterOrDigit() }.takeLast(6).uppercase().padStart(6, '0')
        "SS$tail"
    }

    val academicLine = remember(firstName, lastName, email) {
        val name = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
        when {
            name.isNotBlank() -> "$name | StudySync member"
            email.isNotBlank() -> "${email.substringBefore("@")} | StudySync"
            else -> "StudySync member"
        }
    }
    val defaultDisplay = stringResource(R.string.profile_default_display_name)

    Scaffold(
        containerColor = pageBg,
        topBar = {
            StudySyncProfileTopBar(
                onMenuClick = onOpenMenu,
                onSearchClick = onSearchFriends,
                onSettingsClick = onOpenSettings,
                appName = stringResource(R.string.app_name)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(coverBrush)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 56.dp)
                ) {
                    Box {
                        ProfileAvatar(
                            displayName = displayInitials,
                            avatarUrl = avatarUrl,
                            size = 112.dp
                        )
                        Surface(
                            onClick = { imagePicker.launch("image/*") },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = (-4).dp, y = (-4).dp)
                                .size(36.dp),
                            tonalElevation = 4.dp,
                            shadowElevation = 4.dp
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Change photo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(72.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = displayName.ifBlank { defaultDisplay },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = academicLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.profile_account_id_label, studentId),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "About ${displayName.substringBefore(" ").ifBlank { "you" }}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            "0 Posts",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Text("·", color = MaterialTheme.colorScheme.outline)
                        Text(
                            "$friendsCount Friends",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text("·", color = MaterialTheme.colorScheme.outline)
                        Text(
                            "$groupsCount Groups",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Posts") },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("About") },
                    icon = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        SamplePostCard(
                            authorName = displayName.ifBlank { "You" },
                            initials = displayInitials,
                            body = stringResource(R.string.profile_sample_post_body)
                        )
                    }
                    1 -> {
                        OutlinedTextField(
                            value = bio,
                            onValueChange = { bio = it },
                            label = { Text("Bio") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    isEditing = !isEditing
                                    if (!isEditing) {
                                        onSave(firstName, lastName, displayName, phone, avatarUrl)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isEditing) "Save profile" else "Edit profile")
                            }
                        }

                        AnimatedVisibility(
                            visible = isEditing,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = displayName,
                                    onValueChange = { displayName = it },
                                    label = { Text("Display name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = firstName,
                                    onValueChange = { firstName = it },
                                    label = { Text("First name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = lastName,
                                    onValueChange = { lastName = it },
                                    label = { Text("Surname") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = {},
                                    label = { Text("Email") },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = false,
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = { phone = it },
                                    label = { Text("Phone") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                                Text(
                                    "Contact",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                IntroRow(icon = Icons.Default.Person, text = displayName.ifBlank { "Add your display name" })
                                Divider(modifier = Modifier.padding(vertical = 10.dp))
                                IntroRow(icon = Icons.Default.Email, text = email.ifBlank { "No email" })
                                Divider(modifier = Modifier.padding(vertical = 10.dp))
                                IntroRow(icon = Icons.Default.Phone, text = phone.ifBlank { "No phone number" })
                            }
                        }

                        FilledTonalButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("App settings & appearance")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SamplePostCard(
    authorName: String,
    initials: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(authorName, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Just now",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = {}) { Text("♥ 0") }
                TextButton(onClick = {}) { Text("💬 0") }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(
    displayName: String,
    avatarUrl: String,
    size: androidx.compose.ui.unit.Dp
) {
    val initials = displayName.trim().take(2).ifBlank { "?" }.uppercase()
    val borderColor = Color.White
    val model: Any? = when {
        avatarUrl.isBlank() -> null
        avatarUrl.startsWith("content://") || avatarUrl.startsWith("file://") || avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://") ->
            Uri.parse(avatarUrl)
        else -> null
    }

    Box(
        modifier = Modifier
            .size(size)
            .border(4.dp, borderColor, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = "Profile photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun IntroRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
