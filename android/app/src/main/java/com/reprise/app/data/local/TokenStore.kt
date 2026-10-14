@file:Suppress("DEPRECATION")

package com.reprise.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the signed-in user's JWT on the device.
 *
 * Why encrypted storage? The token is a bearer credential: anyone who reads it
 * can act as the user until it expires. `EncryptedSharedPreferences` encrypts
 * both the keys and the values with a key held in the Android Keystore, so the
 * file is useless if it is copied off the device.
 *
 * Note: Jetpack Security is in maintenance mode (Google now recommends the
 * Keystore directly), but it remains the simplest correct option and its
 * behaviour is stable.
 *
 * The token is the ONLY thing we persist about the session — the user's profile
 * is re-fetched from the API, so it can never go stale.
 *
 * The `@file:Suppress("DEPRECATION")` above is deliberate: Jetpack Security is
 * in maintenance mode, but it is still the simplest correct way to encrypt a
 * value at rest, and its behaviour is stable.
 */
class TokenStore(context: Context) {

    private val preferences: SharedPreferences = createEncryptedPreferences(context)

    /** Persist the JWT returned by register/login. */
    fun saveToken(token: String) {
        preferences.edit().putString(KEY_TOKEN, token).apply()
    }

    /** The stored JWT, or null when nobody is signed in. */
    fun getToken(): String? = preferences.getString(KEY_TOKEN, null)

    /** Forget the session — used by logout and when the token is rejected. */
    fun clear() {
        preferences.edit().remove(KEY_TOKEN).apply()
    }

    private companion object {
        const val FILE_NAME = "reprise_secure_session"
        const val KEY_TOKEN = "jwt"

        fun createEncryptedPreferences(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
