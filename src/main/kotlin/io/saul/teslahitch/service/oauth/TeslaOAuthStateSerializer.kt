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
    @Volatile
    var current: TeslaOAuthState? = null

    @Synchronized
    fun updateState(state: TeslaOAuthState) {
        logger.info("Storing current AuthState.")
        file.parentFile?.mkdirs()
        mapper.writeValue(file, state)
        current = state
    }

    @Synchronized
    fun readState(): TeslaOAuthState? {
        val cached = current
        if (cached != null && System.currentTimeMillis() < cached.refreshTokenExpiresOn) {
            return cached
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

    @Synchronized
    fun wipeState() {
        file.delete()
        current = null
    }
}
