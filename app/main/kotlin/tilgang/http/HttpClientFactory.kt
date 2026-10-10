package tilgang.http

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
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
        // retryOnTimeout = false: timeouts (HttpRequestTimeoutException) retryes IKKE. Kansellering
        // (CancellationException) retryes heller aldri.
        retryOnException(maxRetries = 2, retryOnTimeout = false)
        exponentialDelay()
    }
    install(HttpTimeout) {
        requestTimeoutMillis = timeout.inWholeMilliseconds
    }
    expectSuccess = true
}
