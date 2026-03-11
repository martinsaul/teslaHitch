package io.saul.teslahitch.service.oauth

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileNotFoundException

@Service
class TeslaOAuthStateSerializer(
    val mapper: ObjectMapper,
    @Value("\${tesla.config.dir:/config}") configDir: String
) {
    private val logger = LoggerFactory.getLogger(TeslaOAuthStateSerializer::class.java)
    private val file = File(configDir, "auth.json")
    var current: TeslaOAuthState? = null

    fun updateState(state: TeslaOAuthState) {
        logger.info("Storing current AuthState.")
        file.parentFile?.mkdirs()
        mapper.writeValue(file, state)
        current = state
    }

    fun readState(): TeslaOAuthState? {
        if (current != null && System.currentTimeMillis() < current!!.refreshTokenExpiresOn) {
            return current
        }

        try {
            val state = mapper.readValue(file, TeslaOAuthState::class.java)
            if (System.currentTimeMillis() >= state.refreshTokenExpiresOn) {
                logger.info("Previous OAuth record's refresh token has expired.")
                return null
            }
            current = state
            return state
        } catch (e: FileNotFoundException) {
            logger.info("No previous OAuth state was found.")
            return null
        } catch (e: Exception) {
            logger.warn("Failed to read OAuth state: {}", e.message)
            return null
        }
    }

    fun wipeState() {
        file.delete()
        current = null
    }
}
