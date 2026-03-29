package io.saul.teslahitch.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.saul.teslahitch.service.oauth.TeslaApiLocale
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.io.File

@Service
class TeslaPartnerService(
    @Value("\${tesla.oauth.clientId}") private val clientId: String,
    @Value("\${tesla.oauth.clientSecret}") private val clientSecret: String,
    @Value("\${tesla.callback.hostname:tesla.zenithnetwork.com}") private val domain: String,
    @Value("\${tesla.config.dir:/config}") private val configDir: String,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(TeslaPartnerService::class.java)

    @Volatile
    private var _registered: Boolean = false
    val isRegistered: Boolean get() = _registered

    private val registrationFile: File get() = File(configDir, "partner-registered.json")

    init {
        if (registrationFile.exists()) {
            _registered = true
            logger.info("Partner registration state restored from disk — skipping re-registration.")
        }
    }

    companion object {
        private const val TOKEN_URL = "https://auth.tesla.com/oauth2/v3/token"

        private val PARTNER_SCOPES = listOf(
            "openid",
            "vehicle_device_data",
            "vehicle_cmds",
            "vehicle_charging_cmds"
        )
    }

    fun registerPartner(locale: TeslaApiLocale = TeslaApiLocale.NAAP, force: Boolean = false) {
        if (_registered && !force) {
            logger.info("Partner already registered, skipping. Use force=true to re-register.")
            return
        }
        try {
            val partnerToken = obtainPartnerToken(locale)
            registerDomain(partnerToken, locale)
            _registered = true
            persistRegistration()
        } catch (e: Exception) {
            logger.error("Partner registration failed: {}", e.message, e)
        }
    }

    private fun persistRegistration() {
        try {
            val data = mapOf("domain" to domain, "registeredAt" to System.currentTimeMillis())
            registrationFile.writeText(objectMapper.writeValueAsString(data))
            logger.info("Partner registration state saved to disk.")
        } catch (e: Exception) {
            logger.warn("Failed to persist registration state: {}", e.message)
        }
    }

    private fun obtainPartnerToken(locale: TeslaApiLocale): String {
        logger.info("Obtaining partner token via client_credentials grant...")

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED

        val body = LinkedMultiValueMap<String, String>()
        body.add("grant_type", "client_credentials")
        body.add("client_id", clientId)
        body.add("client_secret", clientSecret)
        body.add("scope", PARTNER_SCOPES.joinToString(" "))
        body.add("audience", locale.url)

        val request = HttpEntity(body, headers)
        val response = restTemplate.postForEntity(TOKEN_URL, request, String::class.java)

        if (!response.statusCode.is2xxSuccessful || response.body == null) {
            throw RuntimeException("Failed to obtain partner token: ${response.statusCode}")
        }

        val json = objectMapper.readTree(response.body)
        val token = json.get("access_token").asText()
        logger.info("Partner token obtained successfully.")
        return token
    }

    private fun registerDomain(partnerToken: String, locale: TeslaApiLocale) {
        logger.info("Registering domain '{}' with Tesla partner API...", domain)

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(partnerToken)

        val body = mapOf("domain" to domain)
        val request = HttpEntity(body, headers)

        val url = "${locale.url}/api/1/partner_accounts"
        val response = restTemplate.postForEntity(url, request, String::class.java)

        if (!response.statusCode.is2xxSuccessful) {
            throw RuntimeException("Partner registration failed: ${response.statusCode} - ${response.body}")
        }

        val json = objectMapper.readTree(response.body)
        val registeredDomain = json.path("response").path("domain").asText()
        logger.info("Partner registration successful. Domain: {}", registeredDomain)
    }
}
