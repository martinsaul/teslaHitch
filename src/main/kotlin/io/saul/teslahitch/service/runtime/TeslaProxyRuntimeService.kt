package io.saul.teslahitch.service.runtime

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service


@Service
class TeslaProxyRuntimeService {
    private val logger = LoggerFactory.getLogger(TeslaProxyRuntimeService::class.java)
    @PostConstruct
    fun init() {
        try {
            TeslaKeyGenRuntime().run()
            TeslaProxyRuntime().run()
        }catch (e: Exception) {logger.error(":(",e)}
    }

}