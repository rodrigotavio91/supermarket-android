package com.barcodescanner.app.data.api

import com.barcodescanner.app.data.auth.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.atomic.AtomicBoolean

class TokenRefreshAuthenticator(
    private val authRepository: AuthRepository
) : Authenticator {
    private val isRefreshing = AtomicBoolean(false)

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= MAX_RETRIES) {
            return null
        }

        if (!isRefreshing.compareAndSet(false, true)) {
            return null
        }

        return try {
            val session = runBlocking {
                val refreshToken = authRepository.getStoredSession()?.refreshToken
                if (refreshToken.isNullOrBlank()) {
                    authRepository.bootstrapSession()
                } else {
                    authRepository.refreshSession(refreshToken)
                }
            }

            if (session?.accessToken.isNullOrBlank()) {
                null
            } else {
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${session?.accessToken}")
                    .build()
            }
        } finally {
            isRefreshing.set(false)
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    companion object {
        private const val MAX_RETRIES = 2
    }
}
