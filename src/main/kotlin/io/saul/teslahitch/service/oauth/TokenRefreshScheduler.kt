package io.saul.teslahitch.service.oauth

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class TokenRefreshScheduler(
    private val oAuthService: TeslaOAuthService,
    private val serializer: TeslaOAuthStateSerializer
) {
    private val logger = LoggerFactory.getLogger(TokenRefreshScheduler::class.java)

    /**
     * Proactively refresh the access token every 4 hours.
     * Tesla access tokens last ~8 hours, so refreshing at 4h keeps them
     * always valid and prevents consumers from needing to refresh independently.
     */
    @Scheduled(fixedDelay = 4 * 60 * 60 * 1000L, initialDelay = 60_000L)
    fun refreshTokenPeriodically() {
        if (!oAuthService.isAuthenticated()) return

        try {
            logger.info("Scheduled token refresh starting...")
            oAuthService.getAccessToken()
            logger.info("Scheduled token refresh completed.")
        } catch (e: Exception) {
            logger.error("Scheduled token refresh failed: {}", e.message, e)
        }
    }
}
