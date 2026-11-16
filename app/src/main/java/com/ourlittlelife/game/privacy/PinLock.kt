package com.ourlittlelife.game.privacy

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The PIN that guards the private side on this phone. Only a salted hash is kept, in this app's own
 * storage; the PIN itself is never saved, never leaves the phone, and isn't shared with your partner.
 */
object PinLock {

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    private fun prefs(context: Context) = context.getSharedPreferences("private-lock", Context.MODE_PRIVATE)

    fun isSet(context: Context): Boolean = prefs(context).contains("hash")

    fun set(context: Context, pin: String) {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        prefs(context).edit().putString("salt", salt).putString("hash", hash(salt, pin)).apply()
    }

    fun matches(context: Context, pin: String): Boolean {
        val salt = prefs(context).getString("salt", null) ?: return false
        return prefs(context).getString("hash", null) == hash(salt, pin)
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).joinToString("") { "%02x".format(it) }
}
