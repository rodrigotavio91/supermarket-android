package com.barcodescanner.app.data.auth

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

interface AuthStorageContract {
    fun getDeviceKey(): String?
    fun getOrCreateDeviceKey(): String
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun getAccessTokenExpiresAt(): String?
    fun saveSession(accessToken: String, refreshToken: String, accessTokenExpiresAt: String)
    fun clearSession()
}

class AuthStorage(context: Context) : AuthStorageContract {
    private val preferences = createPreferences(context)

    override fun getDeviceKey(): String? {
        return preferences.getString(KEY_DEVICE_KEY, null)
    }

    override fun getOrCreateDeviceKey(): String {
        val existing = getDeviceKey()
        if (!existing.isNullOrBlank()) {
            return existing
        }

        val created = UUID.randomUUID().toString()
        preferences.edit()
            .putString(KEY_DEVICE_KEY, created)
            .apply()
        return created
    }

    override fun getAccessToken(): String? {
        return preferences.getString(KEY_ACCESS_TOKEN, null)
    }

    override fun getRefreshToken(): String? {
        return preferences.getString(KEY_REFRESH_TOKEN, null)
    }

    override fun getAccessTokenExpiresAt(): String? {
        return preferences.getString(KEY_ACCESS_TOKEN_EXPIRES_AT, null)
    }

    override fun saveSession(accessToken: String, refreshToken: String, accessTokenExpiresAt: String) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_ACCESS_TOKEN_EXPIRES_AT, accessTokenExpiresAt)
            .apply()
    }

    override fun clearSession() {
        preferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_ACCESS_TOKEN_EXPIRES_AT)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_DEVICE_KEY = "device_key"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ACCESS_TOKEN_EXPIRES_AT = "access_token_expires_at"

        private fun createPreferences(context: Context) = runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            Log.w(TAG, "Falling back to unencrypted SharedPreferences", it)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        private const val TAG = "AuthStorage"
    }
}
