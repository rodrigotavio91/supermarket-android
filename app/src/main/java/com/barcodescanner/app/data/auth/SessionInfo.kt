package com.barcodescanner.app.data.auth

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class SessionInfo(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: String
) {
    fun isAccessExpired(now: Instant = Instant.now()): Boolean {
        return try {
            val expiresAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .withZone(ZoneOffset.UTC)
                .parse(accessTokenExpiresAt, Instant::from)
            now.isAfter(expiresAt)
        } catch (e: Exception) {
            true
        }
    }
}
