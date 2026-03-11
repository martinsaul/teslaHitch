package io.saul.teslahitch.service.oauth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TeslaTeslaOAuthStateSerializerTest {

    private val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `readState returns null when no file exists`() {
        val serializer = TeslaOAuthStateSerializer(mapper, tempDir.absolutePath)
        assertNull(serializer.readState())
    }

    @Test
    fun `updateState and readState round-trip`() {
        val serializer = TeslaOAuthStateSerializer(mapper, tempDir.absolutePath)
        val state = TeslaOAuthState(
            createdOn = System.currentTimeMillis(),
            accessToken = "test-access-token",
            accessTokenExpiresOn = System.currentTimeMillis() + 3600_000,
            refreshToken = "test-refresh-token",
            refreshTokenExpiresOn = System.currentTimeMillis() + 30L * 24 * 3600_000
        )

        serializer.updateState(state)
        val loaded = serializer.readState()

        assertNotNull(loaded)
        assertEquals(state.accessToken, loaded.accessToken)
        assertEquals(state.refreshToken, loaded.refreshToken)
    }

    @Test
    fun `readState returns null when refresh token is expired`() {
        val serializer = TeslaOAuthStateSerializer(mapper, tempDir.absolutePath)
        val state = TeslaOAuthState(
            createdOn = 0,
            accessToken = "expired",
            accessTokenExpiresOn = 1,
            refreshToken = "expired",
            refreshTokenExpiresOn = 1 // already expired
        )

        serializer.updateState(state)
        serializer.current = null // force re-read from disk
        assertNull(serializer.readState())
    }

    @Test
    fun `wipeState deletes file and clears cache`() {
        val serializer = TeslaOAuthStateSerializer(mapper, tempDir.absolutePath)
        val state = TeslaOAuthState(
            createdOn = System.currentTimeMillis(),
            accessToken = "to-be-wiped",
            accessTokenExpiresOn = System.currentTimeMillis() + 3600_000,
            refreshToken = "to-be-wiped",
            refreshTokenExpiresOn = System.currentTimeMillis() + 30L * 24 * 3600_000
        )

        serializer.updateState(state)
        serializer.wipeState()
        assertNull(serializer.readState())
    }
}
