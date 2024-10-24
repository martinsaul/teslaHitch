package io.saul.teslahitch.service.oauth

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileNotFoundException

@Service
class TeslaOAuthStateSerializer (val mapper: ObjectMapper) {
    private val logger = LoggerFactory.getLogger(TeslaOAuthService::class.java)

    val file = File("auth.json")
    var current: TeslaOAuthState? = null

    fun updateState(state: TeslaOAuthState){
        logger.info("Storing current AuthState.")
        mapper.writeValue(file, state)
        current = state;
    }

    fun readState(): TeslaOAuthState? {
        try {
            val state = mapper.readValue(file, TeslaOAuthState::class.java)
            if (System.currentTimeMillis() < state.refreshTokenExpiresOn) {
                logger.info("Previous oAuth record's refresh token expired.")
                return state
            }
            return null
        } catch (fileNotFoundException: FileNotFoundException) {
            logger.info("No previous oAuth state was found.")
            return null
        }
    }

    fun wipeState() {
        file.delete()
    }
}