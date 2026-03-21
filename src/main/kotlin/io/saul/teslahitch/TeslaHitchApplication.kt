package io.saul.teslahitch

import io.saul.teslahitch.service.CertificateService
import io.saul.teslahitch.service.TeslaPartnerService
import io.saul.teslahitch.service.oauth.TeslaOAuthService
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.stereotype.Component

@SpringBootApplication
@EnableScheduling
class TeslaHitchApplication

@Component
class StartupRegistration(
    private val oAuthService: TeslaOAuthService,
    private val partnerService: TeslaPartnerService
) : ApplicationRunner {
    private val logger = org.slf4j.LoggerFactory.getLogger(StartupRegistration::class.java)

    override fun run(args: ApplicationArguments?) {
        if (!oAuthService.isAuthenticated()) return

        Thread {
            val maxAttempts = 5
            for (attempt in 1..maxAttempts) {
                logger.info("Partner registration attempt {}/{}...", attempt, maxAttempts)
                partnerService.registerPartner()
                if (partnerService.isRegistered) {
                    return@Thread
                }
                if (attempt < maxAttempts) {
                    logger.info("Retrying in 15 seconds (waiting for external route)...")
                    Thread.sleep(15_000)
                }
            }
            logger.error("Partner registration failed after {} attempts. Registration may need to be triggered manually via /internal/auth.", maxAttempts)
        }.apply {
            name = "partner-registration"
            isDaemon = true
            start()
        }
    }
}

fun main(args: Array<String>) {
    // Generate certs before Spring starts so SSL config can find them
    CertificateService.ensureCertsExist(
        configDir = System.getenv("TESLA_CONFIG_DIR") ?: System.getProperty("tesla.config.dir", "/config"),
        callbackHostname = System.getenv("TESLA_CALLBACK_HOSTNAME") ?: "tesla.zenithnetwork.com"
    )
    runApplication<TeslaHitchApplication>(*args)
}
