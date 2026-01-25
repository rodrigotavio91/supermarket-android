package com.barcodescanner.app.data.auth

import com.barcodescanner.app.data.auth.model.DeviceAuthRequest
import com.barcodescanner.app.data.auth.model.RefreshTokenRequest
import com.barcodescanner.app.data.auth.model.SessionResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class AuthRepositoryTest {
    @Test
    fun ensureSession_returnsStoredSession_whenNotExpired() = runTest {
        val storage = FakeStorage().apply {
            saveSession("access", "refresh", "2099-01-01T00:00:00Z")
        }
        val service = FakeAuthService()
        val repository = AuthRepository(service, storage)

        val session = repository.ensureSession()

        assertEquals("access", session?.accessToken)
        assertEquals(0, service.bootstrapCalls)
        assertEquals(0, service.refreshCalls)
    }

    @Test
    fun ensureSession_refreshes_whenExpired_withRefreshToken() = runTest {
        val storage = FakeStorage().apply {
            saveSession("access", "refresh", "2000-01-01T00:00:00Z")
        }
        val service = FakeAuthService(refreshResponse = SessionResponse(
            accessToken = "new-access",
            refreshToken = "new-refresh",
            accessTokenExpiresAt = "2099-01-01T00:00:00Z"
        ))
        val repository = AuthRepository(service, storage)

        val session = repository.ensureSession()

        assertEquals("new-access", session?.accessToken)
        assertEquals(0, service.bootstrapCalls)
        assertEquals(1, service.refreshCalls)
        assertEquals("new-access", storage.getAccessToken())
    }

    @Test
    fun ensureSession_bootstraps_whenExpired_withoutRefreshToken() = runTest {
        val storage = FakeStorage().apply {
            saveSession("access", "", "2000-01-01T00:00:00Z")
        }
        val service = FakeAuthService(bootstrapResponse = SessionResponse(
            accessToken = "boot-access",
            refreshToken = "boot-refresh",
            accessTokenExpiresAt = "2099-01-01T00:00:00Z"
        ))
        val repository = AuthRepository(service, storage)

        val session = repository.ensureSession()

        assertEquals("boot-access", session?.accessToken)
        assertEquals(1, service.bootstrapCalls)
        assertEquals(0, service.refreshCalls)
        assertEquals("boot-access", storage.getAccessToken())
    }

    @Test
    fun refreshSession_clearsStorage_onFailure() = runTest {
        val storage = FakeStorage().apply {
            saveSession("access", "refresh", "2099-01-01T00:00:00Z")
        }
        val service = FakeAuthService(refreshThrows = true)
        val repository = AuthRepository(service, storage)

        val session = repository.refreshSession("refresh")

        assertNull(session)
        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())
        assertNull(storage.getAccessTokenExpiresAt())
    }

    private class FakeAuthService(
        private val bootstrapResponse: SessionResponse? = null,
        private val refreshResponse: SessionResponse? = null,
        private val refreshThrows: Boolean = false
    ) : AuthApiService {
        var bootstrapCalls = 0
        var refreshCalls = 0

        override suspend fun bootstrap(request: DeviceAuthRequest): SessionResponse {
            bootstrapCalls++
            return bootstrapResponse
                ?: SessionResponse("boot-access", "boot-refresh", "2099-01-01T00:00:00Z")
        }

        override suspend fun refresh(request: RefreshTokenRequest): SessionResponse {
            refreshCalls++
            if (refreshThrows) {
                throw IllegalStateException("refresh failed")
            }
            return refreshResponse
                ?: SessionResponse("refresh-access", "refresh-refresh", "2099-01-01T00:00:00Z")
        }
    }

    private class FakeStorage : AuthStorageContract {
        private var deviceKey: String? = null
        private var accessToken: String? = null
        private var refreshToken: String? = null
        private var expiresAt: String? = null

        override fun getDeviceKey(): String? = deviceKey

        override fun getOrCreateDeviceKey(): String {
            if (deviceKey == null) {
                deviceKey = "device-${Instant.now().toEpochMilli()}"
            }
            return deviceKey as String
        }

        override fun getAccessToken(): String? = accessToken

        override fun getRefreshToken(): String? = refreshToken

        override fun getAccessTokenExpiresAt(): String? = expiresAt

        override fun saveSession(accessToken: String, refreshToken: String, accessTokenExpiresAt: String) {
            this.accessToken = accessToken
            this.refreshToken = refreshToken
            this.expiresAt = accessTokenExpiresAt
        }

        override fun clearSession() {
            accessToken = null
            refreshToken = null
            expiresAt = null
        }
    }
}
