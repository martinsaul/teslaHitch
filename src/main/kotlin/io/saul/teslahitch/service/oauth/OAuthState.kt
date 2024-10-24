package io.saul.teslahitch.service.oauth

data class OAuthState(
    val refreshToken: String,
    val createdOn: Long, //UNIX Timestamp
    val expiresOn: Long, //UNIX Timestamp
)
