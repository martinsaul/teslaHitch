package io.saul.teslahitch.controller

import io.saul.teslahitch.service.TeslaProxyClient
import io.saul.teslahitch.service.oauth.TeslaOAuthService
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class PrivateController(
    private val oAuthService: TeslaOAuthService,
    private val proxyClient: TeslaProxyClient
) {
    private val logger = LoggerFactory.getLogger(PrivateController::class.java)

    @GetMapping("/internal/")
    fun root(): Map<String, Any> {
        return mapOf(
            "service" to "teslaHitch",
            "authenticated" to oAuthService.isAuthenticated()
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
        return mapOf("status" to "authenticated")
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
