package io.saul.teslahitch.service

import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

class TeslaOAuthServiceTest {

    @Test
    fun test(){
        TeslaOAuthService(clientId = "", clientSecret = "", redirectUri = "").gen()
    }
}