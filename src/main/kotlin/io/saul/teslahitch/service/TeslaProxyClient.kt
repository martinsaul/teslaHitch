package io.saul.teslahitch.service

import io.saul.teslahitch.service.oauth.TeslaOAuthService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class TeslaProxyClient(
    private val oAuthService: TeslaOAuthService,
    private val restTemplate: RestTemplate,
    @Value("\${tesla.proxy.url:https://tesla_http_proxy:4443}") private val proxyUrl: String
) {
    private val logger = LoggerFactory.getLogger(TeslaProxyClient::class.java)

    fun sendCommand(vin: String, endpoint: String, body: String? = null): ResponseEntity<String> {
        val token = oAuthService.getAccessToken()
        val headers = HttpHeaders()
        headers.setBearerAuth(token)
        headers.set("Content-Type", "application/json")

        val url = "$proxyUrl/api/1/vehicles/$vin/$endpoint"
        logger.info("Sending command to proxy: {}", url)

        val request = HttpEntity(body, headers)
        return restTemplate.exchange(url, if (body != null) HttpMethod.POST else HttpMethod.GET, request, String::class.java)
    }
}
