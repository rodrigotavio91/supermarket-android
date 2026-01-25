package com.barcodescanner.app.data.auth

import com.barcodescanner.app.data.auth.model.DeviceAuthRequest
import com.barcodescanner.app.data.auth.model.RefreshTokenRequest
import com.barcodescanner.app.data.auth.model.SessionResponse
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface AuthApiService {
    @Headers("Accept: application/json", "Content-Type: application/json")
    @POST("api/authentication")
    suspend fun bootstrap(@Body request: DeviceAuthRequest): SessionResponse

    @Headers("Accept: application/json", "Content-Type: application/json")
    @POST("session/refresh")
    suspend fun refresh(@Body request: RefreshTokenRequest): SessionResponse
}
