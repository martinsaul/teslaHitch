package io.saul.teslahitch.service

import com.google.common.hash.Hashing
import io.saul.teslahitch.service.TeslaOAuthService.Constants.genStartingString
import java.security.SecureRandom

class TeslaOAuthService(val clientId: String, val clientSecret: String, val redirectUri:String) {

    private var lastKey: String = genStartingString()

    fun gen(){
        val nonce = generateRandom256Characters()
        val state = generateRandom256Characters()
    }

    private fun generateRandom256Characters(): String {
        lastKey = Hashing.sha256().hashString(lastKey, Charsets.UTF_8).toString()
        return lastKey
    }

    private object Constants {
        const val apiPath: String = "https://auth.tesla.com/oauth2/v3/authorize";
        const val responseType: String = "code";
        private val random: SecureRandom = SecureRandom()

        fun genStartingString(): String {
            val byteArray = ByteArray(128)
            random.nextBytes(byteArray)
            return Hashing.sha256().hashBytes(byteArray).toString();
        }
    }
}