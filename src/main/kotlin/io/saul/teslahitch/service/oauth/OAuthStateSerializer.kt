package io.saul.teslahitch.service.oauth

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.io.File

@Service
class OAuthStateSerializer (val mapper: ObjectMapper) {
    val file = File("auth.json")
    var current: OAuthState? = null

    fun updateState(state: OAuthState){
        mapper.writeValue(file, state)
        current = state;
    }

    fun readState(): OAuthState? {
        return mapper.readValue(file, OAuthState::class.java)
    }
}