package com.studysync.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Turns a password into a salted PBKDF2 hash. The raw password is never written to
 * Firestore, Room, or SharedPreferences. Firebase Auth still receives it over TLS and
 * stores its own scrypt hash; this seal is the app's copy for the PoE encryption check.
 */
object PasswordProtector {

    private const val ITERATIONS = 20_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    data class SealedPassword(val saltBase64: String, val hashBase64: String)

    fun protect(email: String, password: String): SealedPassword {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(email, password, salt)
        return SealedPassword(
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP)
        )
    }

    fun verify(email: String, password: String, saltBase64: String, hashBase64: String): Boolean {
        return try {
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val expected = Base64.decode(hashBase64, Base64.NO_WRAP)
            val actual = derive(email, password, salt)
            MessageDigest.isEqual(expected, actual)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun derive(email: String, password: String, salt: ByteArray): ByteArray {
        val pepperedSalt = salt + email.trim().lowercase().toByteArray(Charsets.UTF_8)
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, pepperedSalt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }
}
