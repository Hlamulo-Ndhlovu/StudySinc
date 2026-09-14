package com.studysync.ui.call

import android.util.Base64
import org.json.JSONObject

/**
 * Best-effort JWT payload read (no signature verify). Used to surface expired dev tokens early.
 */
object LiveKitJwtHelper {

    fun isExpired(token: String): Boolean {
        val exp = readExpEpochSeconds(token) ?: return false
        return System.currentTimeMillis() / 1000L >= exp
    }

    fun readExpEpochSeconds(token: String): Long? {
        val parts = token.split('.')
        if (parts.size < 2) return null
        return try {
            val json = decodePayload(parts[1])
            if (json.has("exp")) json.getLong("exp") else null
        } catch (_: Exception) {
            null
        }
    }

    private fun decodePayload(b64Url: String): JSONObject {
        val bytes = try {
            Base64.decode(b64Url, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (_: IllegalArgumentException) {
            val pad = (4 - b64Url.length % 4) % 4
            Base64.decode(
                b64Url + "=".repeat(pad),
                Base64.URL_SAFE or Base64.NO_WRAP
            )
        }
        return JSONObject(String(bytes, Charsets.UTF_8))
    }
}
