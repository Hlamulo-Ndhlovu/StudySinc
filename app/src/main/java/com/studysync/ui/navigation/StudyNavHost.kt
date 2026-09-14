package com.studysync.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.studysync.AppSettings
import com.studysync.data.DashboardSnapshot
import com.studysync.data.StudyRepository
import com.studysync.gamification.Gamification
import com.studysync.ui.auth.AuthScreen
import com.studysync.ui.auth.AuthViewModel
import com.studysync.ui.chat.ChatScreen
import com.studysync.ui.chat.ChatViewModel
import com.studysync.ui.chat.ChatViewModelFactory
import com.studysync.ui.classes.ClassesScreen
import com.studysync.ui.classes.ClassesViewModel
import com.studysync.ui.classes.ClassDetailScreen
import com.studysync.ui.classes.ClassDetailViewModel
import com.studysync.ui.dashboard.DashboardScreen
import com.studysync.ui.dashboard.DashboardViewModel
import com.studysync.ui.chats.ChatsListScreen
import com.studysync.ui.notifications.AppNotificationsScreen
import com.studysync.ui.settings.SettingsScreen
import com.studysync.ui.profile.ProfileScreen
import com.studysync.ui.resources.ResourcesScreen
import com.studysync.ui.resources.ResourcesViewModel
import com.studysync.ui.friends.FriendsScreen
import com.studysync.ui.friends.FriendsViewModel
import com.studysync.ui.gamification.ProgressScreen
import com.studysync.ui.groups.GroupsScreen
import com.studysync.ui.groups.GroupsViewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.studysync.R
import kotlinx.coroutines.launch
import com.studysync.ui.call.CallScreen
import com.studysync.ui.call.LiveKitRoomNames

@Composable
fun StudyNavHost(
    navController: NavHostController,
    repository: StudyRepository,
    settings: AppSettings,
    startDestination: String,
    onOpenDrawer: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val scope = rememberCoroutineScope()
    val useDarkTheme = settings.useDarkTheme.collectAsState().value
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.padding(contentPadding)
    ) {
        composable(Screen.Auth.route) {
            val vm = viewModel<AuthViewModel>(factory = AuthViewModel.factory(repository))
            val context = LocalContext.current
            val webClientId = context.getString(R.string.default_web_client_id)
            val googleLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    val idToken = account?.idToken
                    if (idToken.isNullOrBlank()) {
                        vm.onGoogleSignInResult(
                            null,
                            context.getString(R.string.google_sign_in_no_id_token)
                        )
                    } else {
                        vm.onGoogleSignInResult(idToken)
                    }
                } catch (e: ApiException) {
                    val msg = when (e.statusCode) {
                        GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> null
                        GoogleSignInStatusCodes.DEVELOPER_ERROR ->
                            context.getString(R.string.google_sign_in_developer_error)
                        else -> e.message ?: "Google sign-in failed (${e.statusCode})"
                    }
                    vm.onGoogleSignInResult(null, msg)
                }
            }
            val onGoogleClick = remember(webClientId, context, vm) {
                {
                    if (!isConfiguredFirebaseWebClientId(webClientId)) {
                        vm.onGoogleSignInResult(
                            null,
                            context.getString(R.string.google_sign_in_web_client_missing)
                        )
                    } else {
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(webClientId.trim())
                            .requestEmail()
                            .build()
                        val client = GoogleSignIn.getClient(context, gso)
                        googleLauncher.launch(client.signInIntent)
                    }
                }
            }
            AuthScreen(
                state = vm.state.collectAsStateWithLifecycle().value,
                onEmailChange = vm::onEmailChanged,
                onPasswordChange = vm::onPasswordChanged,
                onConfirmPasswordChange = vm::onConfirmPasswordChanged,
                onFirstNameChange = vm::onFirstNameChanged,
                onLastNameChange = vm::onLastNameChanged,
                onPhoneChange = vm::onPhoneChanged,
                onDisplayNameChange = vm::onDisplayNameChanged,
                onSubmit = vm::onSubmit,
                onToggleMode = vm::onToggleMode,
                onGoogleClick = onGoogleClick,
                onPasswordVisibilityToggle = vm::onPasswordVisibilityToggle,
                onConfirmPasswordVisibilityToggle = vm::onConfirmPasswordVisibilityToggle,
                onForgotPassword = vm::onForgotPassword
            )
        }

        composable(Screen.Dashboard.route) {
            val vm = viewModel<DashboardViewModel>(factory = DashboardViewModel.factory(repository, settings))
            val appNotificationsEnabled =
                settings.notificationsEnabled.collectAsStateWithLifecycle(initialValue = true).value
            DashboardScreen(
                state = vm.state.collectAsStateWithLifecycle().value,
                onOpenGroups = { navController.navigate(Screen.Groups.route) },
                onOpenClasses = { navController.navigate(Screen.Classes.route) },
                onOpenChats = { navController.navigate(Screen.Chats.route) },
                onOpenMenu = onOpenDrawer,
                appNotificationsEnabled = appNotificationsEnabled,
                onOpenAppNotifications = { navController.navigate(Screen.AppNotifications.route) },
                onOpenProfile = { navController.navigate(Screen.Profile.route) },
                onViewAllQuickActions = { navController.navigate(Screen.Chats.route) },
                onOpenProgress = { navController.navigate(Screen.Progress.route) }
            )
        }

        composable(Screen.Groups.route) {
            val vm = viewModel<GroupsViewModel>(factory = GroupsViewModel.factory(repository))
            GroupsScreen(
                state = vm.state.collectAsStateWithLifecycle().value,
                onCreateGroup = vm::createGroup,
                onGroupSelected = { navController.navigate(Screen.Chat.route(it.id)) },
                onNewGroupNameChange = vm::onGroupNameChanged,
                onNewGroupSubjectChange = vm::onGroupSubjectChanged,
                onToggleDialog = vm::setDialogOpen,
                onOpenMenu = onOpenDrawer
            )
        }

        composable(Screen.Friends.route) {
            val vm = viewModel<FriendsViewModel>(factory = FriendsViewModel.factory(repository))
            val user by repository.currentUser.collectAsStateWithLifecycle()
            val userInitial = user?.displayName?.trim()?.firstOrNull()?.uppercaseChar()?.toString()
                ?: user?.email?.trim()?.firstOrNull()?.uppercaseChar()?.toString()
                ?: "?"
            FriendsScreen(
                state = vm.state.collectAsStateWithLifecycle().value,
                userInitial = userInitial,
                onNameChange = vm::onNameChange,
                onEmailChange = vm::onEmailChange,
                onAddFriend = vm::addFriend,
                onToggleDialog = vm::setDialog,
                onOpenMenu = onOpenDrawer
            )
        }

        composable(Screen.Classes.route) {
            val vm = viewModel<ClassesViewModel>(factory = ClassesViewModel.factory(repository))
            val notificationsEnabled =
                settings.notificationsEnabled.collectAsStateWithLifecycle(initialValue = true).value
            ClassesScreen(
                state = vm.state.collectAsStateWithLifecycle().value,
                notificationsEnabled = notificationsEnabled,
                onTitleChange = vm::onTitleChange,
                onTopicChange = vm::onTopicChange,
                onStartChange = vm::onStartTimeChange,
                onDurationChange = vm::onDurationChange,
                onMeetingLinkChange = vm::onMeetingLinkChange,
                onLiveToggle = vm::onLiveToggle,
                onDescriptionChange = vm::onDescriptionChange,
                onLocationChange = vm::onLocationChange,
                onInstructorChange = vm::onInstructorChange,
                onCreateClass = vm::createClass,
                onToggleDialog = vm::setDialog,
                onClassSelected = { navController.navigate(Screen.ClassDetail.route(it.id)) },
                onNavigateToCreatedClass = { classId ->
                    navController.navigate(Screen.ClassDetail.route(classId)) {
                        popUpTo(Screen.Classes.route) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onConsumedNavigateToCreatedClass = vm::consumedNavigateToCreatedClass,
                onConsumedClassCreatedEvent = vm::consumedClassCreatedEvent,
                onOpenMenu = onOpenDrawer
            )
        }

        composable(
            Screen.ClassDetail.route,
            arguments = listOf(navArgument("classId") { type = NavType.StringType }),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "studysync://invite/class/{classId}"
                }
            )
        ) { backStackEntry ->
            val classId = backStackEntry.arguments?.getString("classId").orEmpty()
            val vm = viewModel<ClassDetailViewModel>(factory = ClassDetailViewModel.factory(classId, repository))
            val classDetailState = vm.state.collectAsStateWithLifecycle().value
            LaunchedEffect(classDetailState.pendingNavigateBackAfterDelete) {
                if (classDetailState.pendingNavigateBackAfterDelete) {
                    navController.popBackStack()
                    vm.consumedDeleteNavigation()
                }
            }
            ClassDetailScreen(
                state = classDetailState,
                onBack = { navController.popBackStack() },
                onToggleLive = vm::toggleLive,
                onJoinCall = {
                    val session = vm.state.value.session
                    val liveRoom = LiveKitRoomNames.canonicalRoomId(
                        classId = classId,
                        roomName = session?.roomName.orEmpty(),
                        meetingLink = session?.meetingLink
                    )
                    navController.navigate(Screen.Call.route(liveRoom, classId))
                },
                onDeleteClass = vm::deleteClass,
                onUploadNote = { name, size -> vm.uploadClassNote(name, size) },
                onDeleteNote = vm::deleteClassNote
            )
        }

        composable(Screen.Chats.route) {
            val groups = repository.observeGroups().collectAsStateWithLifecycle(emptyList()).value
            ChatsListScreen(
                groups = groups,
                onChatSelected = { navController.navigate(Screen.Chat.route(it.id)) },
                onOpenMenu = onOpenDrawer
            )
        }

        composable(Screen.Settings.route) {
            val selectedLanguage = settings.language.collectAsStateWithLifecycle().value
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSignOut = {
                    repository.logout()
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                darkModeEnabled = useDarkTheme,
                onToggleDarkMode = { settings.setDarkTheme(it) },
                selectedLanguage = selectedLanguage,
                onLanguageSelected = { settings.setLanguage(it) },
                onOpenAppNotifications = { navController.navigate(Screen.AppNotifications.route) }
            )
        }

        composable(Screen.Progress.route) {
            val snapshot by repository.observeDashboard()
                .collectAsStateWithLifecycle(initialValue = DashboardSnapshot())
            val streak by settings.streakDays.collectAsStateWithLifecycle(initialValue = 0)
            ProgressScreen(
                progress = Gamification.from(snapshot, streak),
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AppNotifications.route) {
            val appNotificationsEnabled =
                settings.notificationsEnabled.collectAsStateWithLifecycle(initialValue = true).value
            AppNotificationsScreen(
                appNotificationsEnabled = appNotificationsEnabled,
                onToggleAppNotifications = { settings.setNotificationsEnabled(it) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Profile.route) {
            val user by repository.currentUser.collectAsStateWithLifecycle()
            val friends by repository.observeFriends().collectAsStateWithLifecycle(initialValue = emptyList())
            val groups by repository.observeGroups().collectAsStateWithLifecycle(initialValue = emptyList())
            ProfileScreen(
                initialDisplayName = user?.displayName.orEmpty(),
                initialFirstName = user?.firstName.orEmpty(),
                initialLastName = user?.lastName.orEmpty(),
                initialEmail = user?.email.orEmpty(),
                initialPhone = user?.phoneNumber.orEmpty(),
                initialAvatarUrl = user?.avatarUrl.orEmpty(),
                userId = user?.id.orEmpty(),
                friendsCount = friends.size,
                groupsCount = groups.size,
                onSave = { first, last, display, phone, avatar ->
                    scope.launch {
                        repository.updateProfile(
                            firstName = first,
                            lastName = last,
                            displayName = display,
                            phoneNumber = phone,
                            avatarUrl = avatar
                        )
                    }
                },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onOpenMenu = onOpenDrawer,
                onSearchFriends = { navController.navigate(Screen.Friends.route) }
            )
        }

        composable(
            Screen.Chat.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            val factory = remember(groupId, repository) {
                ChatViewModelFactory(groupId = groupId, repository = repository)
            }
            val vm = viewModel<ChatViewModel>(factory = factory)

            ChatScreen(
                state = vm.state.collectAsStateWithLifecycle().value,
                onBack = { navController.popBackStack() },
                onMessageChange = vm::onMessageChanged,
                onSend = vm::sendMessage,
                onOpenResources = { navController.navigate(Screen.Resources.route(groupId)) }
            )
        }

        composable(
            Screen.Resources.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            val vm = viewModel<ResourcesViewModel>(factory = ResourcesViewModel.factory(groupId, repository))
            ResourcesScreen(
                state = vm.state.collectAsStateWithLifecycle().value,
                onPickFile = vm::uploadFromPicker,
                onDownload = vm::download,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Screen.Call.route,
            arguments = listOf(
                navArgument("roomName") { type = NavType.StringType },
                navArgument("classId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val rawRoom = backStackEntry.arguments?.getString("roomName").orEmpty()
            val roomName = Uri.decode(rawRoom)
            val classIdArg = Uri.decode(backStackEntry.arguments?.getString("classId").orEmpty())
            CallScreen(
                roomName = roomName,
                classId = classIdArg,
                repository = repository,
                onLeave = { navController.popBackStack() }
            )
        }
    }
}

/** True when [default_web_client_id] is set to a real Firebase *Web* OAuth client id (not the placeholder). */
private fun isConfiguredFirebaseWebClientId(raw: String): Boolean {
    val t = raw.trim()
    if (t.length < 30) return false
    if (t.contains("REPLACE", ignoreCase = true)) return false
    return true
}
