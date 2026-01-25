package com.barcodescanner.app.data.auth

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

data class SessionInfo(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: String
) {

    private var accessTokenExpiresAtParseFailed: Boolean = false

    fun isAccessExpired(now: Instant = Instant.now()): Boolean {
        if (accessTokenExpiresAtParseFailed) {
            // Previous parsing failed; treat expiry as unknown rather than expired.
            return false
        }

        return try {
            val expiresAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .withZone(ZoneOffset.UTC)
                .parse(accessTokenExpiresAt, Instant::from)
            now.isAfter(expiresAt)
        } catch (e: Exception) {
            accessTokenExpiresAtParseFailed = true
            LOGGER.warning(
                "Failed to parse accessTokenExpiresAt '$accessTokenExpiresAt' as ISO_OFFSET_DATE_TIME: ${e.message}"
            )
            false
        }
    }

    companion object {
        private val LOGGER: Logger = Logger.getLogger(SessionInfo::class.java.name)
    }
}
