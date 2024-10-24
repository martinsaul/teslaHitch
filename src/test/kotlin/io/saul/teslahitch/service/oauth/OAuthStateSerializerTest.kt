package io.saul.teslahitch.service.oauth

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test

private val oAuthState = OAuthState(
    createdOn = 0,
    accessToken = "AAA", // https://www.youtube.com/watch?app=desktop&v=Us34mxYZz3I
    accessTokenExpiresOn = 1,
    refreshToken = "aAAAAA",
    refreshTokenExpiresOn = 2
)

@SpringBootTest
class OAuthStateSerializerTest() {
    @Test
    fun test(@Autowired serializer: OAuthStateSerializer){
        serializer.updateState(oAuthState)
        serializer.readState()
    }
}