package com.barcodescanner.app.data.auth.model

import com.google.gson.annotations.SerializedName

data class DeviceAuthRequest(
    @SerializedName("device_key")
    val deviceKey: String
)

data class RefreshTokenRequest(
    @SerializedName("refresh_token")
    val refreshToken: String
)
