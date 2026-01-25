package com.barcodescanner.app.data.auth

import android.content.Context
import android.util.Log
import com.barcodescanner.app.data.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AuthManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val authRepository = ApiClient.getInstance(appContext).authRepositoryProvider

    fun warmUpSession() {
        scope.launch {
            runCatching {
                authRepository.ensureSession()
            }.onFailure { error ->
                Log.w(TAG, "Failed to warm up session", error)
            }
        }
    }

    fun clearSession() {
        authRepository.clearSession()
    }

    companion object {
        private const val TAG = "AuthManager"

        @Volatile
        private var instance: AuthManager? = null

        fun getInstance(context: Context): AuthManager {
            return instance ?: synchronized(this) {
                instance ?: AuthManager(context).also { instance = it }
            }
        }
    }
}
