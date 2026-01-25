package com.barcodescanner.app.data.api

import com.barcodescanner.app.data.auth.AuthRepository
import com.barcodescanner.app.data.auth.SessionInfo
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenRefreshAuthenticatorTest {
    @Test
    fun authenticate_returnsNull_whenMaxRetriesReached() {
        val authenticator = TokenRefreshAuthenticator(FakeAuthRepository())
        val response = responseWithPriorCount(2)

        val result = authenticator.authenticate(null, response)

        assertNull(result)
    }

    @Test
    fun authenticate_refreshes_whenRefreshTokenPresent() {
        val repository = FakeAuthRepository(
            storedRefreshToken = "refresh-token",
            refreshSessionResult = SessionInfo("new-access", "new-refresh", "2099-01-01T00:00:00Z")
        )
        val authenticator = TokenRefreshAuthenticator(repository)
        val response = responseWithPriorCount(0)

        val result = authenticator.authenticate(null, response)

        assertEquals(1, repository.refreshCalls)
        assertEquals(0, repository.bootstrapCalls)
        assertEquals("Bearer new-access", result?.header("Authorization"))
    }

    @Test
    fun authenticate_bootstraps_whenRefreshMissing() {
        val repository = FakeAuthRepository(
            storedRefreshToken = null,
            bootstrapSessionResult = SessionInfo("boot-access", "boot-refresh", "2099-01-01T00:00:00Z")
        )
        val authenticator = TokenRefreshAuthenticator(repository)
        val response = responseWithPriorCount(0)

        val result = authenticator.authenticate(null, response)

        assertEquals(0, repository.refreshCalls)
        assertEquals(1, repository.bootstrapCalls)
        assertEquals("Bearer boot-access", result?.header("Authorization"))
    }

    @Test
    fun authenticate_bootstraps_whenRefreshReturnsNull() {
        val repository = FakeAuthRepository(
            storedRefreshToken = "refresh-token",
            refreshSessionResult = null,
            bootstrapSessionResult = SessionInfo("boot-access", "boot-refresh", "2099-01-01T00:00:00Z")
        )
        val authenticator = TokenRefreshAuthenticator(repository)
        val response = responseWithPriorCount(0)

        val result = authenticator.authenticate(null, response)

        assertEquals(1, repository.refreshCalls)
        assertEquals(1, repository.bootstrapCalls)
        assertEquals("Bearer boot-access", result?.header("Authorization"))
    }

    private fun responseWithPriorCount(priorCount: Int): Response {
        var response: Response? = null
        repeat(priorCount) { index ->
            val request = Request.Builder()
                .url("https://example.com/$index")
                .build()
            response = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .priorResponse(response)
                .build()
        }

        val request = Request.Builder()
            .url("https://example.com/final")
            .build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .priorResponse(response)
            .build()
    }

    private class FakeAuthRepository(
        private val storedRefreshToken: String? = null,
        private val refreshSessionResult: SessionInfo? = null,
        private val bootstrapSessionResult: SessionInfo? = null
    ) : AuthRepository(
        authApiService = DummyAuthService(),
        storage = DummyStorage()
    ) {
        var refreshCalls = 0
        var bootstrapCalls = 0

        override fun getStoredSession(): SessionInfo? {
            return storedRefreshToken?.let {
                SessionInfo("stored-access", it, "2000-01-01T00:00:00Z")
            }
        }

        override suspend fun refreshSession(refreshToken: String): SessionInfo? {
            refreshCalls++
            return refreshSessionResult
        }

        override suspend fun bootstrapSession(): SessionInfo? {
            bootstrapCalls++
            return bootstrapSessionResult
        }
    }

    private class DummyAuthService : com.barcodescanner.app.data.auth.AuthApiService {
        override suspend fun bootstrap(
            request: com.barcodescanner.app.data.auth.model.DeviceAuthRequest
        ): com.barcodescanner.app.data.auth.model.SessionResponse {
            throw UnsupportedOperationException("not used")
        }

        override suspend fun refresh(
            request: com.barcodescanner.app.data.auth.model.RefreshTokenRequest
        ): com.barcodescanner.app.data.auth.model.SessionResponse {
            throw UnsupportedOperationException("not used")
        }
    }

    private class DummyStorage : com.barcodescanner.app.data.auth.AuthStorageContract {
        override fun getDeviceKey(): String? = null
        override fun getOrCreateDeviceKey(): String = "dummy"
        override fun getAccessToken(): String? = null
        override fun getRefreshToken(): String? = null
        override fun getAccessTokenExpiresAt(): String? = null
        override fun saveSession(accessToken: String, refreshToken: String, accessTokenExpiresAt: String) = Unit
        override fun clearSession() = Unit
    }
}
