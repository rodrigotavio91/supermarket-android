package com.barcodescanner.app.data.api

import com.barcodescanner.app.data.auth.AuthRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenRefreshAuthenticator(
    private val authRepository: AuthRepository
) : Authenticator {
    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= MAX_RETRIES) {
            return null
        }

        return runBlocking {
            refreshMutex.withLock {
                val session = run {
                    val refreshToken = authRepository.getStoredSession()?.refreshToken
                    if (refreshToken.isNullOrBlank()) {
                        authRepository.bootstrapSession()
                    } else {
                        authRepository.refreshSession(refreshToken)
                            ?: authRepository.bootstrapSession()
                    }
                }

                val accessToken = session?.accessToken
                if (accessToken.isNullOrBlank()) {
                    null
                } else {
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $accessToken")
                        .build()
                }
            }
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
