package com.studysync.ui.call

import com.studysync.BuildConfig

/**
 * Supplies the LiveKit JWT for joining a room.
 *
 * **Self-hosted:** Tokens are signed with the same API key + secret you configure in `livekit.yaml`
 * on your server. Your backend (recommended) or `livekit-cli token create` can mint short-lived JWTs.
 * See: https://docs.livekit.io/home/server/generating-tokens/
 *
 * **Production:** `POST` to your backend with [roomName] and user id; return the JWT (never embed
 * long-lived secrets in the app). **Development:** set `LIVEKIT_TOKEN` in project-root `local.properties`
 * (see `local.properties.example`); `app/build.gradle` injects it into [BuildConfig].
 *
 * **Room name:** The app joins LiveKit using `class-{firebaseClassId}`. Your dev token must allow
 * that room (or use a token grant that lists this room / wildcard) or the server will disconnect.
 *
 * **401 / "region settings":** LiveKit Cloud rejects tokens minted for a different project than the app’s
 * `LIVEKIT_URL`, or tokens that have expired. Regenerate in the same cloud project as the WebSocket URL.
 */
object CallTokenProvider {
    @Suppress("UNUSED_PARAMETER")
    suspend fun fetchToken(roomName: String): String {
        val fromBuild = BuildConfig.LIVEKIT_TOKEN.trim()
        if (fromBuild.isNotEmpty()) return fromBuild
        return ""
    }
}
