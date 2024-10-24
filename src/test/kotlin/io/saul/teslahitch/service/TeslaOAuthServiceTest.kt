package io.saul.teslahitch.service

import io.saul.teslahitch.service.oauth.TeslaOAuthService
import kotlin.test.Test

class TeslaOAuthServiceTest {

    @Test
    fun test(){
        TeslaOAuthService(clientId = "aaaa", redirectUri = "AAAA").funfun()
    }
}