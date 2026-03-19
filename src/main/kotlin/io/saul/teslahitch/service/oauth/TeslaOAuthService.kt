package io.saul.teslahitch.service.oauth

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.hash.Hashing
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder
import java.security.SecureRandom
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime

private const val STANDARD_REFRESH_TOKEN_DURATION_IN_MONTHS = 3L

@Service
class TeslaOAuthService(
    @Value("\${tesla.oauth.clientId}") private val clientId: String,
    @Value("\${tesla.oauth.clientSecret}") private val clientSecret: String,
    @Value("\${tesla.oauth.redirectUri}") private val redirectUri: String,
    private val serializer: TeslaOAuthStateSerializer,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(TeslaOAuthService::class.java)

    companion object {
        private const val AUTH_URL = "https://auth.tesla.com/oauth2/v3"
        private const val TOKEN_URL = "https://auth.tesla.com/oauth2/v3/token"
        private val random = SecureRandom()

        private val SCOPES = listOf(
            "openid",
            "offline_access",
            "user_data",
            "vehicle_device_data",
            "vehicle_cmds",
            "vehicle_charging_cmds",
            "vehicle_location",
            "energy_device_data",
            "energy_cmds"
        )

        private fun generateNonce(): String {
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            return Hashing.sha256().hashBytes(bytes).toString()
        }
    }

    fun getAuthenticationUrl(): String {
        val nonce = generateNonce()
        val state = generateNonce()
        val scope = URLEncoder.encode(SCOPES.joinToString(" "), "UTF-8").replace("+", "%20")
        return "$AUTH_URL/authorize" +
                "?client_id=$clientId" +
                "&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}" +
                "&locale=en-US" +
                "&prompt=login" +
                "&response_type=code" +
                "&scope=$scope" +
                "&state=$state" +
                "&nonce=$nonce"
    }

    fun exchangeCodeForToken(code: String, locale: TeslaApiLocale = TeslaApiLocale.NAAP) {
        logger.info("Exchanging authorization code for access token...")

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED

        val body = LinkedMultiValueMap<String, String>()
        body.add("grant_type", "authorization_code")
        body.add("client_id", clientId)
        body.add("client_secret", clientSecret)
        body.add("code", code)
        body.add("redirect_uri", redirectUri)
        body.add("audience", locale.url)
        body.add("scope", SCOPES.joinToString(" "))

        val request = HttpEntity(body, headers)
        val response = restTemplate.postForEntity(TOKEN_URL, request, String::class.java)

        if (!response.statusCode.is2xxSuccessful || response.body == null) {
            throw RuntimeException("Token exchange failed: ${response.statusCode} - ${response.body}")
        }

        val json = objectMapper.readTree(response.body)
        val now = System.currentTimeMillis()

        val state = TeslaOAuthState(
            createdOn = now,
            accessToken = json.get("access_token").asText(),
            accessTokenExpiresOn = now + (json.get("expires_in").asLong() * 1000),
            refreshToken = json.get("refresh_token").asText(),
            refreshTokenExpiresOn = now + (90L * 24 * 60 * 60 * 1000) // 3 months per Tesla docs // 30 days
        )

        serializer.updateState(state)
        logger.info("OAuth tokens stored successfully.")
    }

    fun refreshAccessToken(locale: TeslaApiLocale = TeslaApiLocale.NAAP) {
        val currentState = serializer.readState()
            ?: throw IllegalStateException("No OAuth state found. Please authenticate first.")

        logger.info("Refreshing access token...")

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED

        val body = LinkedMultiValueMap<String, String>()
        body.add("grant_type", "refresh_token")
        body.add("client_id", clientId)
        body.add("refresh_token", currentState.refreshToken)

        val request = HttpEntity(body, headers)
        val response = restTemplate.postForEntity(TOKEN_URL, request, String::class.java)

        if (!response.statusCode.is2xxSuccessful || response.body == null) {
            throw RuntimeException("Token refresh failed: ${response.statusCode} - ${response.body}")
        }

        val json = objectMapper.readTree(response.body)
        val now = System.currentTimeMillis()

        val newState = TeslaOAuthState(
            createdOn = now,
            accessToken = json.get("access_token").asText(),
            accessTokenExpiresOn = now + (json.get("expires_in").asLong() * 1000),
            refreshToken = json.get("refresh_token")?.asText() ?: currentState.refreshToken,
            refreshTokenExpiresOn = now + (90L * 24 * 60 * 60 * 1000) // 3 months per Tesla docs
        )

        serializer.updateState(newState)
        logger.info("Access token refreshed successfully.")
    }

    fun getAccessToken(locale: TeslaApiLocale = TeslaApiLocale.NAAP): String {
        val state = serializer.readState()
            ?: throw IllegalStateException("Not authenticated. Visit /internal/auth to begin OAuth flow.")

        if (System.currentTimeMillis() >= state.accessTokenExpiresOn) {
            refreshAccessToken(locale)
            return serializer.readState()!!.accessToken
        }

        return state.accessToken
    }

    fun isAuthenticated(): Boolean {
        return serializer.readState() != null
    }
}
