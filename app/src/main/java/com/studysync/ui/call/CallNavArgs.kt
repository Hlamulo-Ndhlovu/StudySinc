package com.studysync.ui.call

/**
 * Navigation arguments for Call screen.
 * Note: Currently not used in navigation (roomName is passed as String parameter).
 * Kept for potential future use.
 */
data class CallNavArgs(
    val roomName: String,
    val meetingLink: String
)
