package io.saul.teslahitch.filter

import jakarta.servlet.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.IOException
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(1)
class TrustedEndpointsFilter internal constructor(
    @Value("\${server.trustedPort:null}") trustedPort: String?,
    @Value("\${server.trustedPathPrefix:null}") trustedPathPrefix: String?
) : Filter {
    private val trustedPortNum: Int
    private val trustedPathPrefix: String?
    private val logger = LoggerFactory.getLogger(TrustedEndpointsFilter::class.java)

    init {
        if (trustedPort != null && trustedPathPrefix != null && trustedPathPrefix != "null") {
            trustedPortNum = trustedPort.toIntOrNull() ?: 0
            this.trustedPathPrefix = trustedPathPrefix
        } else {
            trustedPortNum = 0
            this.trustedPathPrefix = null
        }
    }

    @Throws(IOException::class, ServletException::class)
    override fun doFilter(servletRequest: ServletRequest, servletResponse: ServletResponse, filterChain: FilterChain) {
        if (trustedPortNum != 0 && trustedPathPrefix != null) {
            val httpRequest = servletRequest as? HttpServletRequest
            val httpResponse = servletResponse as? HttpServletResponse

            if (httpRequest != null && httpResponse != null) {
                val uri = httpRequest.requestURI
                val port = servletRequest.localPort

                if (uri.startsWith(trustedPathPrefix) && port != trustedPortNum) {
                    logger.warn("Denying request for trusted endpoint {} on untrusted port {}", uri, port)
                    httpResponse.status = 404
                    httpResponse.outputStream.close()
                    return
                }

                if (!uri.startsWith(trustedPathPrefix) && port == trustedPortNum) {
                    logger.warn("Denying request for untrusted endpoint {} on trusted port {}", uri, port)
                    httpResponse.status = 404
                    httpResponse.outputStream.close()
                    return
                }
            }
        }

        filterChain.doFilter(servletRequest, servletResponse)
    }
}
