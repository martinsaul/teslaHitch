package io.saul.teslahitch.service.oauth

import com.google.common.hash.Hashing
import io.saul.teslahitch.model.AuthStatus
import io.saul.teslahitch.model.AuthStatus.AuthenticationState.*
import io.saul.teslahitch.model.TokenRequest
import io.saul.teslahitch.model.TokenResponse
import io.saul.teslahitch.service.HttpClientService
import io.saul.teslahitch.service.oauth.TeslaOAuthService.Constants.generateOAuthLoginUrl
import io.saul.teslahitch.service.oauth.TeslaOAuthService.Constants.tokenEndpoint
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.net.URLEncoder
import java.security.SecureRandom
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime

private const val STANDARD_REFRESH_TOKEN_DURATION_IN_MONTHS = 3L

@Service
class TeslaOAuthService(
    @Value("\${tesla.oauth.clientId:null}") iClientId: String?,
    @Value("\${tesla.oauth.clientSecret:null}") iClientSecret: String?,
    @Value("\${tesla.oauth.redirectUri:null}") iRedirectUri: String?,
    val serializer: TeslaOAuthStateSerializer,
    val httpClient: HttpClientService,
) {
    private val logger = LoggerFactory.getLogger(TeslaOAuthService::class.java)
    private val clientId: String = iClientId ?: throw IllegalArgumentException("Client ID is a required value.")
    private val clientSecret: String =
        iClientSecret ?: throw IllegalArgumentException("Client Secret is a required value.")
    private val redirectUri: String =
        iRedirectUri ?: throw IllegalArgumentException("Redirect URI is a required value.")

    fun getAuthenticationUrl(): String {
        return generateOAuthLoginUrl(clientId = clientId, redirectUri = redirectUri)
    }

    fun registerCallbackCode(callbackCode: String, locale: TeslaApiLocale = TeslaApiLocale.NAAP) {
        val tokenRequest = TokenRequest(
            grantType = "authorization_code",
            clientId = clientId,
            clientSecret = clientSecret,
            code = callbackCode,
            redirectUri = redirectUri
        )
        val response = httpClient.postJson(
            destination = URI.create(tokenEndpoint),
            payload = tokenRequest,
            responseObject = TokenResponse::class.java
        )
        // TODO Move this into a dedicated class. Maybe.
        val timeStamp = ZonedDateTime.now(UTC) // Lock it into UTC to avoid any moving TimeZone shenanigans.
        serializer.updateState(
            state = TeslaOAuthState(
                createdOn = timeStamp.toEpochSecond(),
                accessToken = response.accessToken,
                accessTokenExpiresOn = timeStamp.plusSeconds(response.expiresIn).toEpochSecond(),
                refreshToken = response.refreshToken,
                refreshTokenExpiresOn = timeStamp.plusMonths(STANDARD_REFRESH_TOKEN_DURATION_IN_MONTHS).toEpochSecond()
            )
        )
    }

    fun getAuthStatus(): AuthStatus {
        val state = serializer.readState() ?: return AuthStatus(state = NotAuthenticated)

        val nowWithExpirationBuffer = ZonedDateTime.now().plusHours(1).toEpochSecond()

        return if(state.accessTokenExpiresOn > nowWithExpirationBuffer){
            logger.trace("Existing access token is valid.")
            AuthStatus(state = ValidAccessToken, state.accessTokenExpiresOn)
        } else if(state.refreshTokenExpiresOn > nowWithExpirationBuffer){
            logger.info("Existing access token is stale, refresh token is still valid.")
            AuthStatus(state = ValidRefreshToken, state.refreshTokenExpiresOn)
        } else {
            logger.warn("Existing authentication is stale. Deleting file.")
            serializer.wipeState()
            AuthStatus(state = NotAuthenticated)
        }
    }

    fun rearm() {
        TODO("Not yet implemented")
    }

    private object Constants {
        private const val apiPath: String = "https://auth.tesla.com/oauth2/v3"
        const val tokenEndpoint = "$apiPath/token"
        private val random: SecureRandom = SecureRandom()
        private val scopeList: List<String> = listOf(
            "openid",
            "offline_access",
            "user_data",
            "vehicle_device_data",
            "vehicle_cmds",
            "vehicle_charging_cmds",
            "energy_device_data",
            "energy_cmds"
        )

        private var lastKey: String = genStartingString()

        private fun genStartingString(): String {
            val byteArray = ByteArray(256)
            random.nextBytes(byteArray)
            return Hashing.sha256().hashBytes(byteArray).toString()
        }

        private fun generateRandom256Characters(): String {
            lastKey = Hashing.sha256().hashString(lastKey, Charsets.UTF_8).toString()
            return lastKey
        }

        fun generateOAuthLoginUrl(clientId: String, redirectUri: String): String {
            val nonce = generateRandom256Characters()
            val state = generateRandom256Characters()
            val scope = URLEncoder.encode(scopeList.joinToString(separator = " "), "utf-8").replace("+", "%20")
            return "$apiPath/authorize" +
                    "?client_id=$clientId" +
                    "&redirect_uri=$redirectUri" +
                    "&locale=en-US" +
                    "&prompt=login" +
                    "&response_type=code" +
                    "&scope=$scope" +
                    "&state=$state" +
                    "&nonce=$nonce"
        }
    }
}