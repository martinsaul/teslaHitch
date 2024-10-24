package io.saul.teslahitch.controller

import io.saul.teslahitch.model.Dummy
import io.saul.teslahitch.service.oauth.TeslaOAuthService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class PrivateController(val teslaOAuthService: TeslaOAuthService) {

    @GetMapping("/internal/")
    fun root(): Dummy {
        return Dummy()
    }

    @GetMapping("/internal/auth")
    fun startAuth(response: HttpServletResponse) {
        return response.sendRedirect(teslaOAuthService.getAuthenticationUrl())
    }

    @GetMapping("/internal/callback")
    fun callback(@RequestParam(name = "code") code:String, @RequestParam(name = "issuer") issuer:String): Dummy {
        return Dummy()
    }

}