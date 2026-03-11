package io.saul.teslahitch.controller

import io.saul.teslahitch.service.CertificateService
import io.saul.teslahitch.service.oauth.TeslaOAuthStateSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class PublicController(
    private val certificateService: CertificateService,
    private val oauthStateSerializer: TeslaOAuthStateSerializer,
    @Value("\${tesla.oauth.clientId:}") private val clientId: String,
    @Value("\${tesla.proxy.external.url:}") private val proxyExternalUrl: String
) {

    @GetMapping("/")
    fun root(): String {
        return "teslaHitch is running."
    }

    @GetMapping("/.well-known/appspecific/com.tesla.3p.public-key.pem", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun publicKey(): String {
        return certificateService.getPublicKeyPem()
    }

    @GetMapping("/api/ha/config", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun haConfig(): Map<String, Any?> {
        val state = oauthStateSerializer.readState()
            ?: throw ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "OAuth not completed. Visit /internal/auth on the trusted port first.")

        return mapOf(
            "refresh_token" to state.refreshToken,
            "access_token" to state.accessToken,
            "expiration" to state.accessTokenExpiresOn,
            "client_id" to clientId,
            "proxy_url" to proxyExternalUrl
        )
    }
}
