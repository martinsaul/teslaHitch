package io.saul.teslahitch.service.oauth

import com.google.common.hash.Hashing
import io.saul.teslahitch.service.oauth.TeslaOAuthService.Constants.genStartingString
import io.saul.teslahitch.service.oauth.TeslaOAuthService.Constants.generateOAuthLoginUrl
import java.net.URLEncoder
import java.security.SecureRandom

class TeslaOAuthService(val clientId: String, val redirectUri:String) {

    fun funfun(): String{
        return generateOAuthLoginUrl(clientId = clientId, redirectUri = redirectUri)
    }

    private object Constants {
        private const val apiPath: String = "https://auth.tesla.com/oauth2/v3/authorize";
        private val random: SecureRandom = SecureRandom()
        private var lastKey: String = genStartingString()
        private val scopeList: List<String> = listOf("openid",
                "offline_access",
                "user_data",
                "vehicle_device_data",
                "vehicle_cmds",
                "vehicle_charging_cmds",
                "energy_device_data",
                "energy_cmds")

        private fun genStartingString(): String {
            val byteArray = ByteArray(128)
            random.nextBytes(byteArray)
            return Hashing.sha256().hashBytes(byteArray).toString();
        }

        private fun generateRandom256Characters(): String {
            lastKey = Hashing.sha256().hashString(lastKey, Charsets.UTF_8).toString()
            return lastKey
        }

        fun generateOAuthLoginUrl(clientId: String, redirectUri:String): String {
            val nonce = generateRandom256Characters()
            val state = generateRandom256Characters()
            val scope = URLEncoder.encode(scopeList.joinToString(separator = " "), "utf-8").replace("+", "%20")
            return "$apiPath&client_id=$clientId&locale=en-US&prompt=login&redirect_uri=$redirectUri&response_type=code&scope=$scope&state=$state&nonce=$nonce"
        }

        // https://auth.tesla.com/oauth2/v3/authorize?&client_id=$CLIENT_ID&locale=en-US&prompt=login&redirect_uri=$REDIRECT_URI&response_type=code&scope=openid%20vehicle_device_data%20offline_access&state=$STATE
        // {tesla_path}&client_id=$CLIENT_ID&locale=en-US&prompt=login&redirect_uri=$REDIRECT_URI&response_type=code&scope=openid%20vehicle_device_data%20offline_access&state=$STATE&nonce=$nonce
    }
}