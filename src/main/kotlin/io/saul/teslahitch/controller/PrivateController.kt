package io.saul.teslahitch.controller

import io.saul.teslahitch.model.AuthStatus
import io.saul.teslahitch.model.AuthStatus.AuthenticationState.*
import io.saul.teslahitch.model.Welcome
import io.saul.teslahitch.service.oauth.TeslaApiLocale
import io.saul.teslahitch.service.oauth.TeslaOAuthService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.view.RedirectView

@RestController
class PrivateController(val teslaOAuthService: TeslaOAuthService) {

    @GetMapping("/internal/")
    fun root(): Welcome {
        return Welcome()
    }

    @GetMapping("/internal/authenticate")
    fun authenticate(): RedirectView {
        return RedirectView(teslaOAuthService.getAuthenticationUrl(), false)
    }

    @GetMapping("/internal/rearm")
    fun rearm(): Welcome {
        teslaOAuthService.rearm()
        return Welcome()
    }

    @GetMapping("/internal/callback")
    fun callback(
        @RequestParam(name = "code") code: String,
        @RequestParam(name = "locale") locale: String,
        @RequestParam(name = "issuer") issuer: String,
    ): RedirectView {
        teslaOAuthService.registerCallbackCode(code, TeslaApiLocale.map(locale)) ?: "Huh?"
        return RedirectView("status")
    }

    @GetMapping("/internal/status")
    fun authStatus(response: HttpServletResponse): Any {
        val status = teslaOAuthService.getAuthStatus()
        return when(status.state) {
            NotAuthenticated -> RedirectView("authenticate")
            ValidAccessToken -> status
            ValidRefreshToken -> RedirectView("rearm")
        }
    }
}