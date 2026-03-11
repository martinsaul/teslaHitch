package io.saul.teslahitch.service

import io.saul.teslahitch.service.oauth.TeslaOAuthService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.saul.teslahitch.service.oauth.TeslaOAuthStateSerializer
import org.junit.jupiter.api.io.TempDir
import org.springframework.web.client.RestTemplate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class TeslaOAuthServiceTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `getAuthenticationUrl returns valid Tesla OAuth URL`() {
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val serializer = TeslaOAuthStateSerializer(mapper, tempDir.absolutePath)
        val service = TeslaOAuthService(
            clientId = "test-client-id",
            clientSecret = "test-secret",
            redirectUri = "https://example.com/callback",
            serializer = serializer,
            restTemplate = RestTemplate(),
            objectMapper = mapper
        )

        val url = service.getAuthenticationUrl()
        assertTrue(url.startsWith("https://auth.tesla.com/oauth2/v3/authorize"))
        assertTrue(url.contains("client_id=test-client-id"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("vehicle_cmds"))
    }

    @Test
    fun `isAuthenticated returns false when no state exists`() {
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val serializer = TeslaOAuthStateSerializer(mapper, tempDir.absolutePath)
        val service = TeslaOAuthService(
            clientId = "test-client-id",
            clientSecret = "test-secret",
            redirectUri = "https://example.com/callback",
            serializer = serializer,
            restTemplate = RestTemplate(),
            objectMapper = mapper
        )

        assertTrue(!service.isAuthenticated())
    }
}
