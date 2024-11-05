package io.saul.teslahitch.model

import com.fasterxml.jackson.annotation.JsonProperty

data class TokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("refresh_token") val refreshToken: String,
    @JsonProperty("id_token") val idToken: String,
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("state") val state: String,
    @JsonProperty("token_type") val tokenType: String,
) {
}
