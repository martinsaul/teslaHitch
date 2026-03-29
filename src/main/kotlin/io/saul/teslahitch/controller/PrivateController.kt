package io.saul.teslahitch.controller

import io.saul.teslahitch.service.TeslaPartnerService
import io.saul.teslahitch.service.TeslaProxyClient
import io.saul.teslahitch.service.oauth.TeslaOAuthService
import io.saul.teslahitch.service.oauth.TeslaOAuthState
import io.saul.teslahitch.service.oauth.TeslaOAuthStateSerializer
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class PrivateController(
    private val oAuthService: TeslaOAuthService,
    private val oauthStateSerializer: TeslaOAuthStateSerializer,
    private val proxyClient: TeslaProxyClient,
    private val partnerService: TeslaPartnerService
) {
    private val logger = LoggerFactory.getLogger(PrivateController::class.java)

    @GetMapping("/internal/")
    fun root(): Map<String, Any> {
        val authed = oAuthService.isAuthenticated()
        return mapOf(
            "service" to "teslaHitch",
            "authenticated" to authed,
            "message" to if (authed) "Connected to Tesla. Ready to hitch a ride." else "Not authenticated. Visit /internal/auth to connect your Tesla account."
        )
    }

    @GetMapping("/internal/auth")
    fun startAuth(response: HttpServletResponse) {
        response.sendRedirect(oAuthService.getAuthenticationUrl())
    }

    @GetMapping("/internal/callback")
    fun callback(
        @RequestParam("code") code: String,
        @RequestParam("state") state: String
    ): Map<String, String> {
        logger.info("Received OAuth callback, exchanging code for token...")
        oAuthService.exchangeCodeForToken(code)

        logger.info("OAuth complete. Registering public key with Tesla...")
        partnerService.registerPartner(force = true)

        return mapOf("status" to "authenticated", "message" to "You're in! Tesla account connected and public key registered.")
    }

    @PostMapping("/internal/vehicles/{vin}/command/{endpoint}")
    fun sendCommand(
        @PathVariable vin: String,
        @PathVariable endpoint: String,
        @RequestBody(required = false) body: String?
    ): ResponseEntity<String> {
        return proxyClient.sendCommand(vin, endpoint, body)
    }

    @GetMapping("/internal/vehicles/{vin}/{endpoint}")
    fun getVehicleData(
        @PathVariable vin: String,
        @PathVariable endpoint: String
    ): ResponseEntity<String> {
        return proxyClient.sendCommand(vin, endpoint)
    }

    /**
     * Allows external consumers (e.g. Home Assistant) to sync back updated tokens
     * after they perform a refresh. This prevents refresh token rotation from
     * invalidating teslaHitch's stored refresh token.
     */
    @PostMapping("/internal/token/sync")
    fun syncToken(@RequestBody body: Map<String, Any>): Map<String, String> {
        val accessToken = body["access_token"] as? String
            ?: return mapOf("status" to "error", "message" to "Missing access_token")
        val refreshToken = body["refresh_token"] as? String
            ?: return mapOf("status" to "error", "message" to "Missing refresh_token")
        val expiresIn = (body["expires_in"] as? Number)?.toLong()
            ?: (body["expiration"] as? Number)?.toLong()

        val now = System.currentTimeMillis()
        val accessTokenExpiresOn = if (expiresIn != null && expiresIn < 1_000_000_000_000L) {
            // expires_in is in seconds
            now + (expiresIn * 1000)
        } else if (expiresIn != null) {
            // expiration is an absolute timestamp in ms
            expiresIn
        } else {
            // Default to 8 hours
            now + (8 * 60 * 60 * 1000L)
        }

        val newState = TeslaOAuthState(
            createdOn = now,
            accessToken = accessToken,
            accessTokenExpiresOn = accessTokenExpiresOn,
            refreshToken = refreshToken,
            refreshTokenExpiresOn = now + (90L * 24 * 60 * 60 * 1000)
        )

        oauthStateSerializer.updateState(newState)
        logger.info("Token state synced from external consumer.")
        return mapOf("status" to "ok", "message" to "Token state updated.")
    }
}
