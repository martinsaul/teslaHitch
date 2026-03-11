package io.saul.teslahitch.config

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder
import org.apache.hc.client5.http.ssl.TrustAllStrategy
import org.apache.hc.core5.ssl.SSLContextBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.client.RestTemplate

@Configuration
class BeanConfiguration {

    @Bean
    fun objectMapper(): ObjectMapper {
        return Jackson2ObjectMapperBuilder()
            .modules(KotlinModule.Builder().build())
            .build<ObjectMapper>()
            .setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.ANY)
            .setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @Bean
    fun mappingJackson2HttpMessageConverter(): MappingJackson2HttpMessageConverter {
        val jsonConverter = MappingJackson2HttpMessageConverter()
        jsonConverter.objectMapper = objectMapper()
        return jsonConverter
    }

    @Bean
    fun restTemplate(): RestTemplate {
        // Trust self-signed certs for communication with the Tesla HTTP proxy
        val sslContext = SSLContextBuilder.create()
            .loadTrustMaterial(TrustAllStrategy.INSTANCE)
            .build()

        val sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
            .setSslContext(sslContext)
            .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
            .build()

        val connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            .setSSLSocketFactory(sslSocketFactory)
            .build()

        val httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .build()

        val requestFactory = HttpComponentsClientHttpRequestFactory(httpClient)
        return RestTemplate(requestFactory)
    }
}
