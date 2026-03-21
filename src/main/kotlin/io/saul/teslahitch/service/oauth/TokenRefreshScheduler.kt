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
     * Proactively force-refresh the access token every 2 hours.
     * Tesla access tokens last ~8 hours. By force-refreshing at 2h intervals,
     * the token always has ~8h of remaining life when consumers read it,
     * so HA should never need to refresh independently and trigger token rotation.
     */
    @Scheduled(fixedDelay = 2 * 60 * 60 * 1000L, initialDelay = 60_000L)
    fun refreshTokenPeriodically() {
        if (!oAuthService.isAuthenticated()) return

        try {
            logger.info("Scheduled proactive token refresh starting...")
            oAuthService.forceRefresh()
            logger.info("Scheduled proactive token refresh completed.")
        } catch (e: Exception) {
            logger.error("Scheduled token refresh failed: {}", e.message, e)
        }
    }
}
