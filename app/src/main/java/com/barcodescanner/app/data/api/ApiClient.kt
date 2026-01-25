package com.barcodescanner.app.data.api

import android.content.Context
import com.barcodescanner.app.BuildConfig
import com.barcodescanner.app.data.auth.AuthApiService
import com.barcodescanner.app.data.auth.AuthRepository
import com.barcodescanner.app.data.auth.AuthStorage
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiClient private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val authStorage = AuthStorage(appContext)
    private val authApiService = createAuthService()
    private val authRepository = AuthRepository(authApiService, authStorage)

    val productApiService: ProductApiService by lazy {
        createAuthedRetrofit().create(ProductApiService::class.java)
    }

    val authRepositoryProvider: AuthRepository
        get() = authRepository

    private fun createAuthService(): AuthApiService {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(baseClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApiService::class.java)
    }

    private fun createAuthedRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(authedClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun baseClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private fun authedClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val authInterceptor = AuthHeaderInterceptor { authStorage.getAccessToken() }
        val authenticator = TokenRefreshAuthenticator(authRepository)

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(authInterceptor)
            .authenticator(authenticator)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        @Volatile
        private var instance: ApiClient? = null

        fun getInstance(context: Context): ApiClient {
            return instance ?: synchronized(this) {
                instance ?: ApiClient(context).also { instance = it }
            }
        }
    }
}
