package io.saul.teslahitch.controller

import io.saul.teslahitch.service.CertificateService
import io.saul.teslahitch.service.TeslaPartnerService
import io.saul.teslahitch.service.oauth.TeslaOAuthService
import io.saul.teslahitch.service.oauth.TeslaOAuthStateSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class PublicController(
    private val certificateService: CertificateService,
    private val oauthStateSerializer: TeslaOAuthStateSerializer,
    private val oAuthService: TeslaOAuthService,
    private val partnerService: TeslaPartnerService,
    @Value("\${tesla.oauth.clientId:}") private val clientId: String,
    @Value("\${tesla.proxy.external.url:}") private val proxyExternalUrl: String,
    @Value("\${tesla.callback.hostname:tesla.zenithnetwork.com}") private val domain: String
) {

    @GetMapping("/")
    fun root(): String {
        return "teslaHitch is running."
    }

    @GetMapping("/health")
    fun health(): Map<String, Any> {
        return mapOf(
            "status" to "up",
            "authenticated" to (oauthStateSerializer.readState() != null),
            "partner_registered" to partnerService.isRegistered
        )
    }

    @GetMapping("/health/ready")
    fun ready(): ResponseEntity<Map<String, Any>> {
        val authenticated = oauthStateSerializer.readState() != null
        val registered = partnerService.isRegistered
        val ready = !authenticated || registered
        val status = if (ready) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE
        return ResponseEntity.status(status).body(mapOf(
            "status" to if (ready) "ready" else "registering",
            "authenticated" to authenticated,
            "partner_registered" to registered
        ))
    }

    @GetMapping("/.well-known/appspecific/com.tesla.3p.public-key.pem", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun publicKey(): String {
        return certificateService.getPublicKeyPem()
    }

    @GetMapping("/api/ha/config", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun haConfig(): Map<String, Any?> {
        // Call getAccessToken() first — it may refresh and update the stored state
        val accessToken = oAuthService.getAccessToken()
        // Now read the (potentially updated) state so expiration is correct
        val state = oauthStateSerializer.readState()
            ?: throw ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "OAuth not completed. Visit /internal/auth on the trusted port first.")

        return mapOf(
            "access_token" to accessToken,
            "refresh_token" to state.refreshToken,
            "expiration" to state.accessTokenExpiresOn,
            "client_id" to clientId,
            "proxy_url" to proxyExternalUrl,
            "domain" to domain
        )
    }
}
