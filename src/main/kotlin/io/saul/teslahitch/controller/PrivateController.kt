package io.saul.teslahitch.controller

import io.saul.teslahitch.service.TeslaPartnerService
import io.saul.teslahitch.service.TeslaProxyClient
import io.saul.teslahitch.service.oauth.TeslaOAuthService
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class PrivateController(
    private val oAuthService: TeslaOAuthService,
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
        partnerService.registerPartner()

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
}
