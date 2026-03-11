package io.saul.teslahitch.controller

import io.saul.teslahitch.service.CertificateService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PublicController(private val certificateService: CertificateService) {

    @GetMapping("/")
    fun root(): String {
        return "teslaHitch is running."
    }

    @GetMapping("/.well-known/appspecific/com.tesla.3p.public-key.pem", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun publicKey(): String {
        return certificateService.getPublicKeyPem()
    }
}
