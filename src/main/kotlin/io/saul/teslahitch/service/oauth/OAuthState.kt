package io.saul.teslahitch.service.oauth

import java.io.Serializable

data class OAuthState(
    val createdOn: Long, //UNIX Timestamp
    val accessToken: String,
    val accessTokenExpiresOn: Long, //UNIX Timestamp
    val refreshToken: String,
    val refreshTokenExpiresOn: Long, //UNIX Timestamp
): Serializable
