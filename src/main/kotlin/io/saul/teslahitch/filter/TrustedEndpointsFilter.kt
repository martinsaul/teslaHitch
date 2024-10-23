package io.saul.teslahitch.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.apache.catalina.connector.RequestFacade
import org.apache.catalina.connector.ResponseFacade
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(1)
class TrustedEndpointsFilter internal constructor(@Value("\${server.trustedPort:null}") trustedPort: String?, @Value("\${server.trustedPathPrefix:null}") trustedPathPrefix: String?) :
    Filter {
    private var trustedPortNum = 0
    private var trustedPathPrefix: String? = null
    private val log: Logger = LoggerFactory.getLogger(javaClass.name)

    init {
        if (trustedPort != null && trustedPathPrefix != null && "null" != trustedPathPrefix) {
            trustedPortNum = trustedPort.toInt()
            this.trustedPathPrefix = trustedPathPrefix
        }
    }

    @Throws(IOException::class, ServletException::class)
    override fun doFilter(servletRequest: ServletRequest, servletResponse: ServletResponse, filterChain: FilterChain) {
        if (trustedPortNum != 0) {
            if (isRequestForTrustedEndpoint(servletRequest) && servletRequest.localPort != trustedPortNum) {
                log.warn("denying request for trusted endpoint on untrusted port")
                (servletResponse as ResponseFacade).status = 404
                servletResponse.getOutputStream().close()
                return
            }

            if (!isRequestForTrustedEndpoint(servletRequest) && servletRequest.localPort == trustedPortNum) {
                log.warn("denying request for untrusted endpoint on trusted port")
                (servletResponse as ResponseFacade).status = 404
                servletResponse.getOutputStream().close()
                return
            }
        }

        filterChain.doFilter(servletRequest, servletResponse)
    }

    private fun isRequestForTrustedEndpoint(servletRequest: ServletRequest): Boolean {
        return (servletRequest as RequestFacade).requestURI.startsWith(trustedPathPrefix!!)
    }
}
