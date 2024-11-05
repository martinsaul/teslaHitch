package io.saul.teslahitch.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpClient.Version
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers


@Service
class HttpClientService(val mapper: ObjectMapper) {
    private val client: HttpClient = HttpClient.newBuilder()
        .version(Version.HTTP_2)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun <T> postJson(destination: URI, payload: Any, responseObject:Class<T>): T {
        val serializedJson = mapper.writeValueAsString(payload)
        val request: HttpRequest = HttpRequest.newBuilder().POST(HttpRequest.BodyPublishers.ofString(serializedJson))
            .header("Content-Type", "application/json")
            .uri(destination)
            .build()
        val result = client.send(request, BodyHandlers.ofString())
        when(result.statusCode() / 100) {
            1,3 -> {
                // TODO handle these cases better. We shouldn't experience 100 or 300 series errors here however.
                throw IllegalStateException("Unexpected non error state! Code: ${result.statusCode()}, body: ${result.body()}");
            }
            2 -> {
                return mapper.readValue(result.body(), responseObject)
            }
            else -> {
                throw IllegalStateException("Received error response. Code: ${result.statusCode()}, body: ${result.body()}");
            }
        }
    }
}
