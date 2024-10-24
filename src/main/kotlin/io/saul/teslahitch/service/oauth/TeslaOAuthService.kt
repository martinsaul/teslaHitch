package io.saul.teslahitch.service.oauth

import com.google.common.hash.Hashing
import io.saul.teslahitch.service.oauth.TeslaOAuthService.Constants.generateOAuthCallbackRegistration
import io.saul.teslahitch.service.oauth.TeslaOAuthService.Constants.generateOAuthLoginUrl
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.security.SecureRandom

@Service
class TeslaOAuthService(
    @Value("\${tesla.oauth.clientId:null}") iClientId: String?,
    @Value("\${tesla.oauth.clientSecret:null}") iClientSecret: String?,
    @Value("\${tesla.oauth.redirectUri:null}") iRedirectUri: String?,
    val serializer: TeslaOAuthStateSerializer
) {
    private val clientId: String = iClientId ?: throw IllegalArgumentException("Client ID is a required value.")
    private val clientSecret: String =
        iClientSecret ?: throw IllegalArgumentException("Client Secret is a required value.")
    private val redirectUri: String =
        iRedirectUri ?: throw IllegalArgumentException("Redirect URI is a required value.")

    @PostConstruct
    fun init() {
        getAuthenticationUrl()
    }

    fun getAuthenticationUrl(): String {
        return generateOAuthLoginUrl(clientId = clientId, redirectUri = redirectUri)
    }

    fun registerCallbackCode(callbackCode: String, locale: TeslaApiLocale = TeslaApiLocale.NAAP) {
        val url = generateOAuthCallbackRegistration(
            callBack = callbackCode,
            clientId = clientId,
            clientSecret = clientSecret,
            redirectUri = redirectUri,
            locale = locale
        )
    }

    fun isAuthenticated(): Boolean {
        return serializer.readState() != null
    }

    private object Constants {
        private const val apiPath: String = "https://auth.tesla.com/oauth2/v3/";
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
            return Hashing.sha256().hashBytes(byteArray).toString();
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

        fun generateOAuthCallbackRegistration(
            callBack: String,
            clientId: String,
            clientSecret: String,
            redirectUri: String,
            locale: TeslaApiLocale
        ): String {
            val localeUrl = URLEncoder.encode(locale.url, "UTF-8")
            return "$apiPath/token" +
                    "?client_id=$clientId" +
                    "&clientId=$clientId" +
                    "&clientSecret=$clientSecret" +
                    "&redirect_uri=$redirectUri" +
                    "&code=$callBack" +
                    "&grant_type=authorization_code" +
                    "&audience=$localeUrl"
        }

    }
}