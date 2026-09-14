package com.studysync

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.studysync.data.StudyRepository
import com.studysync.notifications.StudySyncNotifications
import com.studysync.ui.classes.ClassInviteLinks
import com.studysync.ui.navigation.Screen
import com.studysync.ui.navigation.StudyNavHost
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Composable
fun StudySyncApp(
    repository: StudyRepository,
    settings: AppSettings,
    pendingInviteUri: Uri? = null,
    onConsumePendingInvite: () -> Unit = {}
) {
    val currentUser by repository.currentUser.collectAsStateWithLifecycle()
    val language by settings.language.collectAsStateWithLifecycle()
    val context = LocalContext.current
    /** Recreate NavHost when auth session or language changes. */
    val navKey = "${currentUser?.id ?: "signed_out"}-${language.tag}"

    key(navKey) {
        val navController = rememberNavController()
        val scope = rememberCoroutineScope()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val hideBottomBarForCall = navBackStackEntry?.destination?.route
            ?.substringBefore("?")
            ?.startsWith("call/") == true

        val startDestination =
            if (currentUser != null) Screen.Dashboard.route else Screen.Auth.route

    LaunchedEffect(pendingInviteUri, currentUser) {
        val uri = pendingInviteUri ?: return@LaunchedEffect
        if (currentUser == null) return@LaunchedEffect
        when (uri.host?.lowercase()) {
            "friends" -> {
                navController.navigate(Screen.Friends.route) { launchSingleTop = true }
                onConsumePendingInvite()
            }
            else -> {
                val classId = ClassInviteLinks.parseClassId(uri)
                if (classId != null) {
                    navController.navigate(Screen.ClassDetail.route(classId)) {
                        launchSingleTop = true
                    }
                }
                onConsumePendingInvite()
            }
        }
    }

    LaunchedEffect(currentUser?.id) {
        if (currentUser == null) return@LaunchedEffect
        var firstBatch = true
        var knownIds = emptySet<String>()
        combine(
            repository.observeFriendInbound(),
            settings.notificationsEnabled
        ) { inbound, enabled -> inbound to enabled }
            .collect { (inbound, enabled) ->
                if (firstBatch) {
                    knownIds = inbound.map { it.id }.toSet()
                    firstBatch = false
                    return@collect
                }
                val newItems = inbound.filter { it.id !in knownIds }
                knownIds = inbound.map { it.id }.toSet()
                if (newItems.isEmpty() || !enabled) return@collect
                val appCtx = context.applicationContext
                val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        appCtx,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                if (!canPost) return@collect
                for (item in newItems) {
                    val label = item.fromDisplayName.ifBlank {
                        item.fromEmail.ifBlank { "Someone" }
                    }
                    StudySyncNotifications.showFriendAddedYou(appCtx, label, item.id)
                }
            }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val drawerScreens = if (currentUser != null) {
        listOf(
            Screen.Dashboard,
            Screen.Progress,
            Screen.Classes,
            Screen.Friends,
            Screen.Groups,
            Screen.Chats,
            Screen.AppNotifications,
            Screen.Profile,
            Screen.Settings
        )
    } else {
        emptyList()
    }

    val bottomItems = if (currentUser != null) {
        listOf(
            BottomTab(Screen.Dashboard.route, R.string.nav_dashboard, Icons.Default.Home),
            BottomTab(Screen.Classes.route, R.string.nav_classes, Icons.Default.School),
            BottomTab(Screen.Friends.route, R.string.nav_community, Icons.Default.Group),
            BottomTab(Screen.Profile.route, R.string.nav_profile, Icons.Default.Person)
        )
    } else {
        emptyList()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.brand_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.drawer_section_main),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    drawerScreens.forEach { screen ->
                        NavigationDrawerItem(
                            label = { Text(stringResource(drawerLabelRes(screen))) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Dashboard.route) { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = NavigationDrawerItemDefaults.colors()
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (bottomItems.isNotEmpty() && !hideBottomBarForCall) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        val currentRoute = navController.currentBackStackEntry?.destination?.route
                        bottomItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(Screen.Dashboard.route) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        item.icon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            StudyNavHost(
                navController = navController,
                repository = repository,
                settings = settings,
                startDestination = startDestination,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                contentPadding = paddingValues
            )
        }
    }
    }
}

private fun drawerLabelRes(screen: Screen): Int = when (screen) {
    Screen.Dashboard -> R.string.nav_drawer_dashboard
    Screen.Progress -> R.string.nav_drawer_progress
    Screen.Groups -> R.string.nav_drawer_groups
    Screen.Classes -> R.string.nav_drawer_classes
    Screen.Friends -> R.string.nav_drawer_friends
    Screen.Chats -> R.string.nav_drawer_chats
    Screen.AppNotifications -> R.string.nav_drawer_app_notifications
    Screen.Settings -> R.string.nav_drawer_settings
    Screen.Profile -> R.string.nav_drawer_profile
    Screen.Auth, Screen.ClassDetail, Screen.Call, Screen.Resources, Screen.Chat ->
        R.string.app_name
}

private data class BottomTab(val route: String, val labelRes: Int, val icon: ImageVector)
