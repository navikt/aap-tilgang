package tilgang.auth

import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import tilgang.http.createHttpClient
import java.net.URI
import kotlin.time.Duration.Companion.seconds

fun createTexasClient(endpoint: URI): HttpClient = createHttpClient(2.seconds) {
    defaultRequest {
        url(endpoint.toString())
    }
}
