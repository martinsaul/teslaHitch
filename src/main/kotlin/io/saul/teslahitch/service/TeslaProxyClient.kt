package io.saul.teslahitch.service

import io.saul.teslahitch.service.oauth.TeslaApiLocale
import io.saul.teslahitch.service.oauth.TeslaOAuthService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

@Service
class TeslaProxyClient(
    private val oAuthService: TeslaOAuthService,
    private val restTemplate: RestTemplate,
    @Value("\${tesla.proxy.url:https://tesla_http_proxy:4443}") private val proxyUrl: String,
    @Value("\${tesla.fleet.locale:NAAP}") private val fleetLocale: String
) {
    private val logger = LoggerFactory.getLogger(TeslaProxyClient::class.java)

    private val fleetApiUrl: String
        get() = TeslaApiLocale.valueOf(fleetLocale).url

    private fun authHeaders(): HttpHeaders {
        val token = oAuthService.getAccessToken()
        val headers = HttpHeaders()
        headers.setBearerAuth(token)
        headers.set("Content-Type", "application/json")
        return headers
    }

    fun sendCommand(vin: String, endpoint: String, body: String? = null): ResponseEntity<String> {
        val headers = authHeaders()
        val url = "$proxyUrl/api/1/vehicles/$vin/command/$endpoint"
        logger.debug("Proxying POST: {}", endpoint)

        return try {
            val request = HttpEntity(body, headers)
            restTemplate.exchange(url, HttpMethod.POST, request, String::class.java)
        } catch (e: RestClientException) {
            logger.error("Tesla proxy communication error for {}: {}", endpoint, e.message)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("""{"error": "Proxy communication failed"}""")
        }
    }

    fun getVehicleData(vin: String, endpoint: String): ResponseEntity<String> {
        // Vehicle data reads don't need command signing -- go direct to Fleet API
        return fleetApiGet("/api/1/vehicles/$vin/$endpoint")
    }

    fun getEnergySiteData(siteId: String, endpoint: String): ResponseEntity<String> {
        return fleetApiGet("/api/1/energy_sites/$siteId/$endpoint")
    }

    fun sendEnergySiteCommand(siteId: String, endpoint: String, body: String? = null): ResponseEntity<String> {
        val headers = authHeaders()
        val url = "$fleetApiUrl/api/1/energy_sites/$siteId/$endpoint"
        logger.debug("Fleet API energy site POST: {}/{}", siteId, endpoint)

        return try {
            val request = HttpEntity(body, headers)
            restTemplate.exchange(url, HttpMethod.POST, request, String::class.java)
        } catch (e: RestClientException) {
            logger.error("Fleet API energy site error for {}: {}", endpoint, e.message)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("""{"error": "Energy site command failed"}""")
        }
    }

    /**
     * Call the Tesla Fleet API directly (not through the signing proxy).
     * Used for endpoints that don't require vehicle command signing,
     * such as listing vehicles/products.
     */
    fun fleetApiGet(path: String): ResponseEntity<String> {
        val headers = authHeaders()
        val url = "$fleetApiUrl$path"
        logger.debug("Fleet API GET: {}", path)

        return try {
            val request = HttpEntity<String>(null, headers)
            restTemplate.exchange(url, HttpMethod.GET, request, String::class.java)
        } catch (e: RestClientException) {
            logger.error("Fleet API communication error for {}: {}", path, e.message)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("""{"error": "Fleet API communication failed"}""")
        }
    }
}
