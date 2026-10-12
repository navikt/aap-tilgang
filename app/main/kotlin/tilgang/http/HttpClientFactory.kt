package tilgang.http

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import kotlin.time.Duration

fun createHttpClient(timeout: Duration) = HttpClient(CIO) {
    install(ContentNegotiation) {
        jackson {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
    install(HttpRequestRetry) {
        // Retry på 5xx-svar (serverfeil).
        retryOnServerErrors(maxRetries = 2)
        // Retry på transiente nettverks-/I/O-feil (f.eks. connection reset/refused, broken pipe).
        // Timeouts retryes ikke; kansellering retryes heller aldri.
        retryOnExceptionIf(maxRetries = 2) { _, cause ->
            cause !is ConnectTimeoutException &&
                cause !is SocketTimeoutException &&
                cause !is HttpRequestTimeoutException
        }
        exponentialDelay()
    }
    install(HttpTimeout) {
        requestTimeoutMillis = timeout.inWholeMilliseconds
    }
    expectSuccess = true
}
