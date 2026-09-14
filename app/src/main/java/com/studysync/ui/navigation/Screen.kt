package com.studysync.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Dashboard : Screen("dashboard")
    object Groups : Screen("groups")
    object Friends : Screen("friends")
    object Classes : Screen("classes")
    object Chats : Screen("chats")
    object Settings : Screen("settings")
    object Progress : Screen("progress")
    /** In-app + device notification preferences (dashboard bell). */
    object AppNotifications : Screen("appNotifications")
    object Profile : Screen("profile")
    object ClassDetail : Screen("classDetail/{classId}") {
        fun route(classId: String) = "classDetail/$classId"
    }
    object Call : Screen("call/{roomName}?classId={classId}") {
        fun route(liveKitRoomId: String, classId: String = ""): String {
            val encRoom = Uri.encode(liveKitRoomId)
            val base = "call/$encRoom"
            return if (classId.isBlank()) base else "$base?classId=${Uri.encode(classId)}"
        }
    }
    object Resources : Screen("resources/{groupId}") {
        fun route(groupId: String) = "resources/$groupId"
    }
    object Chat : Screen("chat/{groupId}") {
        fun route(groupId: String) = "chat/$groupId"
    }
}
