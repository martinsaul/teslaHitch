package io.saul.teslahitch.service

import io.saul.teslahitch.service.oauth.TeslaOAuthService
import org.slf4j.LoggerFactory
import kotlin.test.Test

class TeslaOAuthServiceTest {

    private val logger = LoggerFactory.getLogger(TeslaOAuthService::class.java)

    @Test
    fun test(){
        val url = TeslaOAuthService(iClientId = "aaaa", iRedirectUri = "AAAA").funfun()
    }
}