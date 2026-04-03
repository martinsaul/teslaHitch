package io.saul.teslahitch.controller

import io.saul.teslahitch.service.CertificateService
import io.saul.teslahitch.service.TeslaPartnerService
import io.saul.teslahitch.service.oauth.TeslaOAuthStateSerializer
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PublicController(
    private val certificateService: CertificateService,
    private val oauthStateSerializer: TeslaOAuthStateSerializer,
    private val partnerService: TeslaPartnerService,
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
}
