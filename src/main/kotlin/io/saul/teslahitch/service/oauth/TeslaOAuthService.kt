package io.saul.teslahitch.service.oauth

import com.fasterxml.jackson.databind.ObjectMapper
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
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val REFRESH_BUFFER_MS = 5 * 60 * 1000L // refresh 5 min before expiry

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
    private val refreshLock = ReentrantLock()

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
            return bytes.joinToString("") { "%02x".format(it) }
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
            throw RuntimeException("Token exchange failed: ${response.statusCode}")
        }

        val json = objectMapper.readTree(response.body)
        val now = System.currentTimeMillis()

        val accessToken = json.get("access_token")?.asText()
            ?: throw IllegalStateException("Missing access_token in token exchange response")
        val expiresIn = json.get("expires_in")?.asLong()
            ?: throw IllegalStateException("Missing expires_in in token exchange response")
        val refreshToken = json.get("refresh_token")?.asText()
            ?: throw IllegalStateException("Missing refresh_token in token exchange response")

        val state = TeslaOAuthState(
            createdOn = now,
            accessToken = accessToken,
            accessTokenExpiresOn = now + (expiresIn * 1000),
            refreshToken = refreshToken,
            refreshTokenExpiresOn = now + (90L * 24 * 60 * 60 * 1000)
        )

        serializer.updateState(state)
        logger.info("OAuth tokens stored successfully.")
    }

    /**
     * Sends a refresh_token grant to Tesla and persists the new state.
     * Must be called while holding refreshLock.
     */
    private fun doRefresh(currentState: TeslaOAuthState) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED

        val body = LinkedMultiValueMap<String, String>()
        body.add("grant_type", "refresh_token")
        body.add("client_id", clientId)
        body.add("refresh_token", currentState.refreshToken)

        val request = HttpEntity(body, headers)
        val response = restTemplate.postForEntity(TOKEN_URL, request, String::class.java)

        if (!response.statusCode.is2xxSuccessful || response.body == null) {
            logger.error("Token refresh failed: {}", response.statusCode)
            throw RuntimeException("Token refresh failed: ${response.statusCode}")
        }

        val json = objectMapper.readTree(response.body)
        val now = System.currentTimeMillis()

        val accessToken = json.get("access_token")?.asText()
            ?: throw IllegalStateException("Missing access_token in refresh response")
        val expiresIn = json.get("expires_in")?.asLong()
            ?: throw IllegalStateException("Missing expires_in in refresh response")

        val newState = TeslaOAuthState(
            createdOn = now,
            accessToken = accessToken,
            accessTokenExpiresOn = now + (expiresIn * 1000),
            refreshToken = json.get("refresh_token")?.asText() ?: currentState.refreshToken,
            refreshTokenExpiresOn = now + (90L * 24 * 60 * 60 * 1000)
        )

        serializer.updateState(newState)
        logger.info("Access token refreshed. Expires in {} seconds.", expiresIn)
    }

    /**
     * Refreshes the access token if it's close to expiring.
     * Guarded by a lock so only one thread refreshes at a time — prevents
     * double-refresh race conditions that would send a rotated refresh token.
     */
    fun refreshAccessToken(locale: TeslaApiLocale = TeslaApiLocale.NAAP) {
        refreshLock.withLock {
            // Re-check after acquiring lock — another thread may have already refreshed
            val currentState = serializer.readState()
                ?: throw IllegalStateException("No OAuth state found. Please authenticate first.")

            if (System.currentTimeMillis() < (currentState.accessTokenExpiresOn - REFRESH_BUFFER_MS)) {
                logger.debug("Token was already refreshed by another thread, skipping.")
                return
            }

            doRefresh(currentState)
        }
    }

    fun getAccessToken(locale: TeslaApiLocale = TeslaApiLocale.NAAP): String {
        val state = serializer.readState()
            ?: throw IllegalStateException("Not authenticated. Visit /internal/auth to begin OAuth flow.")

        if (System.currentTimeMillis() >= (state.accessTokenExpiresOn - REFRESH_BUFFER_MS)) {
            refreshAccessToken(locale)
            return serializer.readState()?.accessToken
                ?: throw IllegalStateException("Token refresh completed but state is null")
        }

        return state.accessToken
    }

    /**
     * Force a refresh regardless of current token expiration.
     * Used by the scheduler to proactively keep tokens fresh so that
     * consumers never need to refresh independently.
     */
    fun forceRefresh(locale: TeslaApiLocale = TeslaApiLocale.NAAP) {
        refreshLock.withLock {
            val currentState = serializer.readState()
                ?: throw IllegalStateException("No OAuth state found. Please authenticate first.")

            logger.info("Force-refreshing access token (scheduled)...")
            doRefresh(currentState)
        }
    }

    fun isAuthenticated(): Boolean {
        return serializer.readState() != null
    }
}
