package io.saul.teslahitch.config

import org.apache.catalina.connector.Connector
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.StringUtils


@Configuration
class PortConfiguration {
    @Value("\${server.port:8080}")
    private val serverPort: String? = null


    @Value("\${management.port:\${server.port:8080}}")
    private val managementPort: String? = null


    @Value("\${server.trustedPort:null}")
    private val trustedPort: String? = null

    @Bean
    fun servletContainer(): WebServerFactoryCustomizer<*> {
        val additionalConnectors: Array<Connector>? = this.additionalConnector()

        return TomcatMultiConnectorServletWebServerFactoryCustomizer(additionalConnectors)
    }


    private fun additionalConnector(): Array<Connector>? {
        if (!StringUtils.hasText(this.trustedPort) || "null" == trustedPort) {
            return null
        }

        val defaultPorts: MutableSet<String?> = HashSet()
        defaultPorts.add(serverPort)
        defaultPorts.add(managementPort)

        if (!defaultPorts.contains(trustedPort)) {
            val connector: Connector = Connector("org.apache.coyote.http11.Http11NioProtocol")
            connector.scheme = "http"
            connector.port = trustedPort?.toIntOrNull()
                ?: throw IllegalArgumentException("Invalid trustedPort value: $trustedPort")
            return arrayOf(connector)
        } else {
            return arrayOf()
        }
    }

    // Spring Boot 4 made TomcatServletWebServerFactoryCustomizer package-private. Boot's own
    // auto-configured customizer still applies the server.* properties, so this one only has
    // to register the extra (trusted) connector.
    private inner class TomcatMultiConnectorServletWebServerFactoryCustomizer(
        private val additionalConnectors: Array<Connector>?
    ) : WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

        override fun customize(factory: TomcatServletWebServerFactory) {
            if (!additionalConnectors.isNullOrEmpty()) {
                additionalConnectors.forEach { factory.addAdditionalConnectors(it) }
            }
        }
    }
}
