package io.saul.teslahitch.config

import org.apache.catalina.connector.Connector
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.web.ServerProperties
import org.springframework.boot.autoconfigure.web.servlet.TomcatServletWebServerFactoryCustomizer
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
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

        val serverProperties = ServerProperties()
        return TomcatMultiConnectorServletWebServerFactoryCustomizer(serverProperties, additionalConnectors)
    }


    private fun additionalConnector(): Array<Connector>? {
        if (StringUtils.isEmpty(this.trustedPort) || "null" == trustedPort) {
            return null
        }

        val defaultPorts: MutableSet<String?> = HashSet()
        defaultPorts.add(serverPort)
        defaultPorts.add(managementPort)

        if (!defaultPorts.contains(trustedPort)) {
            val connector: Connector = Connector("org.apache.coyote.http11.Http11NioProtocol")
            connector.setScheme("http")
            connector.setPort(trustedPort!!.toInt())
            return arrayOf(connector)
        } else {
            return arrayOf()
        }
    }

    private inner class TomcatMultiConnectorServletWebServerFactoryCustomizer(
        serverProperties: ServerProperties?,
        additionalConnectors: Array<Connector>?
    ) :
        TomcatServletWebServerFactoryCustomizer(serverProperties) {
        private val additionalConnectors: Array<Connector>? = additionalConnectors

        override fun customize(factory: TomcatServletWebServerFactory) {
            super.customize(factory)

            if (!additionalConnectors.isNullOrEmpty()) {
                additionalConnectors.forEach { factory.addAdditionalTomcatConnectors(it) }
            }
        }
    }
}
