package com.studysync.data.firebase

import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException

/**
 * Maps Firebase exceptions to short, actionable UI messages.
 * [FirebaseAuthException] "internal error" usually means missing SHA-1 in Firebase or emulator without Google Play.
 */
internal fun Throwable.toAuthUiMessage(): String {
    return when (this) {
        is FirebaseAuthException -> mapAuthException(this)
        is FirebaseFirestoreException -> mapFirestoreException(this)
        else -> {
            val msg = message?.takeIf { it.isNotBlank() }.orEmpty()
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                "Firestore blocked this request (permission denied). In Firebase Console open Firestore → Rules, " +
                    "publish the rules from the project’s firestore.rules file, then try again."
            } else {
                msg.ifBlank { this::class.simpleName ?: "Something went wrong" }
            }
        }
    }
}

private fun mapAuthException(e: FirebaseAuthException): String = when (e.errorCode) {
    "ERROR_INTERNAL_ERROR" ->
        "Firebase Auth could not reach the service. Add your app’s SHA-1 fingerprint in Firebase Console → " +
            "Project settings → Your apps → Android app, then download a fresh google-services.json. " +
            "Use an emulator with Google Play (not “Google APIs” only), or a physical device."
    "ERROR_NETWORK_REQUEST_FAILED" ->
        "Network error. Check your internet connection and try again."
    "ERROR_EMAIL_ALREADY_IN_USE" ->
        "This email is already registered. Try signing in instead."
    "ERROR_WEAK_PASSWORD" ->
        "Password is too weak. Use at least 6 characters."
    "ERROR_INVALID_EMAIL" ->
        "That email address doesn’t look valid."
    "ERROR_USER_DISABLED" ->
        "This account has been disabled."
    "ERROR_TOO_MANY_REQUESTS" ->
        "Too many attempts. Wait a moment and try again."
    else -> e.message ?: e.errorCode
}

private fun mapFirestoreException(e: FirebaseFirestoreException): String = when (e.code.name) {
    "PERMISSION_DENIED" ->
        "Permission denied saving your profile. In Firebase Console → Firestore → Rules, allow signed-in users " +
            "to create/update documents under users/{userId}."
    "UNAVAILABLE" ->
        "Firestore is temporarily unavailable. Check your connection."
    else -> e.message ?: e.code.toString()
}

/**
 * Like [kotlin.runCatching] but the block may call suspend functions (e.g. Firebase [await]).
 * Use this instead of `runCatching { }.mapAuthFailure()` inside coroutines.
 */
internal suspend fun <T> runSuspendCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(Exception(e.toAuthUiMessage()))
    }
