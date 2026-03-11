package io.saul.teslahitch

import io.saul.teslahitch.service.CertificateService
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TeslaHitchApplication

fun main(args: Array<String>) {
    // Generate certs before Spring starts so SSL config can find them
    CertificateService.ensureCertsExist(
        configDir = System.getenv("TESLA_CONFIG_DIR") ?: System.getProperty("tesla.config.dir", "/config"),
        callbackHostname = System.getenv("TESLA_CALLBACK_HOSTNAME") ?: "tesla.zenithnetwork.com"
    )
    runApplication<TeslaHitchApplication>(*args)
}
