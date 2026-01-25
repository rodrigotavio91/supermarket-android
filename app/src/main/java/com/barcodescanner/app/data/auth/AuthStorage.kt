package com.barcodescanner.app.data.auth

import android.content.Context
import java.util.UUID

class AuthStorage(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDeviceKey(): String? {
        return preferences.getString(KEY_DEVICE_KEY, null)
    }

    fun getOrCreateDeviceKey(): String {
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

    fun getAccessToken(): String? {
        return preferences.getString(KEY_ACCESS_TOKEN, null)
    }

    fun getRefreshToken(): String? {
        return preferences.getString(KEY_REFRESH_TOKEN, null)
    }

    fun getAccessTokenExpiresAt(): String? {
        return preferences.getString(KEY_ACCESS_TOKEN_EXPIRES_AT, null)
    }

    fun saveSession(accessToken: String, refreshToken: String, accessTokenExpiresAt: String) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_ACCESS_TOKEN_EXPIRES_AT, accessTokenExpiresAt)
            .apply()
    }

    fun clearSession() {
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
    }
}
