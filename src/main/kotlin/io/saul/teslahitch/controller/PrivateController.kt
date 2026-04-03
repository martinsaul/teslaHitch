package io.saul.teslahitch.controller

import io.saul.teslahitch.service.TeslaPartnerService
import io.saul.teslahitch.service.TeslaProxyClient
import io.saul.teslahitch.service.oauth.TeslaOAuthService
import io.saul.teslahitch.service.oauth.TeslaOAuthState
import io.saul.teslahitch.service.oauth.TeslaOAuthStateSerializer
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
class PrivateController(
    private val oAuthService: TeslaOAuthService,
    private val oauthStateSerializer: TeslaOAuthStateSerializer,
    private val proxyClient: TeslaProxyClient,
    private val partnerService: TeslaPartnerService,
    @Value("\${tesla.oauth.clientId:}") private val clientId: String,
    @Value("\${tesla.proxy.external.url:}") private val proxyExternalUrl: String,
    @Value("\${tesla.callback.hostname:tesla.zenithnetwork.com}") private val domain: String
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

    /**
     * Home Assistant configuration endpoint. Returns tokens, client ID, and proxy URL.
     * On the trusted port only -- these are sensitive credentials.
     */
    @GetMapping("/internal/ha/config", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun haConfig(): Map<String, Any?> {
        val accessToken = oAuthService.getAccessToken()
        val state = oauthStateSerializer.readState()
            ?: throw ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "OAuth not completed. Visit /internal/auth first.")

        return mapOf(
            "access_token" to accessToken,
            "refresh_token" to state.refreshToken,
            "expiration" to state.accessTokenExpiresOn,
            "client_id" to clientId,
            "proxy_url" to proxyExternalUrl,
            "domain" to domain
        )
    }

    /**
     * List all Tesla products (vehicles and energy sites) for the authenticated account.
     */
    @GetMapping("/internal/products", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun listProducts(): ResponseEntity<String> {
        val response = proxyClient.fleetApiGet("/api/1/products")
        return ResponseEntity.status(response.statusCode)
            .contentType(MediaType.APPLICATION_JSON)
            .body(response.body)
    }

    @PostMapping("/internal/vehicles/{vin}/command/{endpoint}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun sendCommand(
        @PathVariable vin: String,
        @PathVariable endpoint: String,
        @RequestBody(required = false) body: String?
    ): ResponseEntity<String> {
        validateVin(vin)
        validateEndpoint(endpoint)
        val response = proxyClient.sendCommand(vin, endpoint, body)
        return ResponseEntity.status(response.statusCode)
            .contentType(MediaType.APPLICATION_JSON)
            .body(response.body)
    }

    @GetMapping("/internal/vehicles/{vin}/{endpoint}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getVehicleData(
        @PathVariable vin: String,
        @PathVariable endpoint: String
    ): ResponseEntity<String> {
        validateVin(vin)
        validateEndpoint(endpoint)
        val response = proxyClient.getVehicleData(vin, endpoint)
        return ResponseEntity.status(response.statusCode)
            .contentType(MediaType.APPLICATION_JSON)
            .body(response.body)
    }

    @GetMapping("/internal/energy_sites/{siteId}/{endpoint}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getEnergySiteData(
        @PathVariable siteId: String,
        @PathVariable endpoint: String
    ): ResponseEntity<String> {
        validateEndpoint(endpoint)
        val response = proxyClient.getEnergySiteData(siteId, endpoint)
        return ResponseEntity.status(response.statusCode)
            .contentType(MediaType.APPLICATION_JSON)
            .body(response.body)
    }

    @PostMapping("/internal/energy_sites/{siteId}/{endpoint}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun sendEnergySiteCommand(
        @PathVariable siteId: String,
        @PathVariable endpoint: String,
        @RequestBody(required = false) body: String?
    ): ResponseEntity<String> {
        validateEndpoint(endpoint)
        val response = proxyClient.sendEnergySiteCommand(siteId, endpoint, body)
        return ResponseEntity.status(response.statusCode)
            .contentType(MediaType.APPLICATION_JSON)
            .body(response.body)
    }

    private fun validateVin(vin: String) {
        require(vin.matches(Regex("^[A-Za-z0-9]{5,17}$"))) {
            "Invalid VIN format: $vin"
        }
    }

    private fun validateEndpoint(endpoint: String) {
        require(endpoint.matches(Regex("^[a-zA-Z0-9_/]+$"))) {
            "Invalid endpoint format: $endpoint"
        }
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
