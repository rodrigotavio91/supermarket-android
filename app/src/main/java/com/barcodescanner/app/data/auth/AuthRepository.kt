package com.barcodescanner.app.data.auth

import android.util.Log
import com.barcodescanner.app.data.auth.model.DeviceAuthRequest
import com.barcodescanner.app.data.auth.model.RefreshTokenRequest
import com.barcodescanner.app.data.auth.model.SessionResponse

class AuthRepository(
    private val authApiService: AuthApiService,
    private val storage: AuthStorage
) {
    suspend fun ensureSession(): SessionInfo? {
        val current = getStoredSession()
        if (current != null && !current.isAccessExpired()) {
            return current
        }

        val refreshToken = current?.refreshToken
        return if (refreshToken.isNullOrBlank()) {
            bootstrapSession()
        } else {
            refreshSession(refreshToken)
        }
    }

    suspend fun bootstrapSession(): SessionInfo? {
        return runCatching {
            val deviceKey = storage.getOrCreateDeviceKey()
            authApiService.bootstrap(DeviceAuthRequest(deviceKey))
        }.onSuccess { response ->
            store(response)
        }.onFailure { error ->
            Log.w(TAG, "Failed to bootstrap session", error)
        }.getOrNull()?.toSessionInfo()
    }

    suspend fun refreshSession(refreshToken: String): SessionInfo? {
        return runCatching {
            authApiService.refresh(RefreshTokenRequest(refreshToken))
        }.onSuccess { response ->
            store(response)
        }.onFailure { error ->
            Log.w(TAG, "Failed to refresh session", error)
            storage.clearSession()
        }.getOrNull()?.toSessionInfo()
    }

    fun clearSession() {
        storage.clearSession()
    }

    fun getStoredSession(): SessionInfo? {
        val accessToken = storage.getAccessToken()
        val refreshToken = storage.getRefreshToken()
        val expiresAt = storage.getAccessTokenExpiresAt()

        if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank() || expiresAt.isNullOrBlank()) {
            return null
        }

        return SessionInfo(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAt = expiresAt
        )
    }

    private fun store(response: SessionResponse) {
        storage.saveSession(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            accessTokenExpiresAt = response.accessTokenExpiresAt
        )
    }

    private fun SessionResponse.toSessionInfo(): SessionInfo {
        return SessionInfo(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAt = accessTokenExpiresAt
        )
    }

    companion object {
        private const val TAG = "AuthRepository"
    }
}
