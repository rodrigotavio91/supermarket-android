package com.barcodescanner.app.data.auth.model

import com.google.gson.annotations.SerializedName

data class SessionResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String,
    @SerializedName("access_token_expires_at")
    val accessTokenExpiresAt: String
)
