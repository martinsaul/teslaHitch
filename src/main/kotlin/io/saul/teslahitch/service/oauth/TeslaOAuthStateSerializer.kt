package io.saul.teslahitch.service.oauth

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileNotFoundException

@Service
class TeslaOAuthStateSerializer(val mapper: ObjectMapper) {
    private val logger = LoggerFactory.getLogger(TeslaOAuthStateSerializer::class.java)

    val file = File("auth.json")
    var current: TeslaOAuthState? = null

    fun updateState(state: TeslaOAuthState) {
        logger.info("Storing current AuthState.")
        mapper.writeValue(file, state)
        current = state
    }

    fun readState(): TeslaOAuthState? {
        try {
            return mapper.readValue(file, TeslaOAuthState::class.java)
        } catch (fileNotFoundException: FileNotFoundException) {
            logger.info("No previous oAuth state was found.")
            return null
        }
    }

    fun wipeState() {
        file.delete()
    }
}