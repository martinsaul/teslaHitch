package io.saul.teslahitch.controller

import io.saul.teslahitch.model.Dummy
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PrivateController {

    @GetMapping("/internal/")
    fun root(): Dummy {
        return Dummy()
    }

}