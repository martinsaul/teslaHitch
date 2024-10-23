package io.saul.teslahitch.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PublicController {

    @GetMapping("/")
    fun blog(): String {
        return "hi there friendo"
    }

}