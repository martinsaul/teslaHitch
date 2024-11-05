package io.saul.teslahitch.model

import io.saul.teslahitch.model.AuthStatus.AuthenticationState.NotAuthenticated

data class AuthStatus(val state: AuthenticationState, val expirationTime: Long? = null){
    init {
        require(state == NotAuthenticated && expirationTime == null || state != NotAuthenticated) {"Expiration time cannot be null for authenticated statuses."}
    }

    enum class AuthenticationState {
        NotAuthenticated, ValidAccessToken, ValidRefreshToken
    }
}
