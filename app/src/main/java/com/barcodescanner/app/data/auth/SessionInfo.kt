package com.barcodescanner.app.data.auth

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class SessionInfo(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: String
) {
    fun isAccessExpired(now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): Boolean {
        return try {
            val expiresAt = OffsetDateTime.parse(accessTokenExpiresAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            now.isAfter(expiresAt)
        } catch (e: Exception) {
            true
        }
    }
}
