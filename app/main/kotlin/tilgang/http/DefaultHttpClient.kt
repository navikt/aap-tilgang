package tilgang.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import kotlin.time.Duration.Companion.seconds

val defaultHttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        jackson {
            configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 2)
        exponentialDelay()
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 10.seconds.inWholeMilliseconds
    }
    expectSuccess = true
}
