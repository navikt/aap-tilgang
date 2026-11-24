package tilgang.integrasjoner.tilgangsmaskin

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.utils.unwrapCancellationException
import io.ktor.serialization.jackson.jackson
import io.ktor.utils.io.ClosedReadChannelException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.EOFException
import java.net.SocketException
import java.nio.channels.ClosedChannelException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Dedikert HTTP-klient for Tilgangsmaskinen, adskilt fra den delte klienten (PDL m.fl.) slik at
 * retry- og timeout-strategien kan tilpasses Tilgangsmaskinen uten å påvirke andre integrasjoner.
 */
object TilgangsmaskinHttpClient {
    // Vi prøver på nytt opptil to ganger for å unngå mange kall til en tjeneste som feiler.
    private const val MAKS_ANTALL_RETRIES = 2
    private val STANDARD_TIMEOUT = 5.seconds
    private val CONNECT_TIMEOUT = 2.seconds
    private const val RETRY_DELAY_MS = 100L
    private const val RETRY_JITTER_MS = 150L

    private fun totalTimeout(timeout: Duration) = createClientPlugin("TilgangsmaskinTotalTimeout") {
        on(Send) { request ->
            withTimeoutOrNull(timeout) {
                proceed(request)
            } ?: throw HttpRequestTimeoutException(request.url.buildString(), timeout.inWholeMilliseconds)
        }
    }

    fun create(
        timeout: Duration = STANDARD_TIMEOUT,
        configureClient: HttpClientConfig<*>.() -> Unit = {},
    ): HttpClient = HttpClient(CIO) {
        configure(timeout)
        configureClient()
    }

    internal fun create(engine: HttpClientEngine, timeout: Duration = STANDARD_TIMEOUT): HttpClient =
        HttpClient(engine) { configure(timeout) }

    private fun HttpClientConfig<*>.configure(timeout: Duration) {
        require(timeout.isFinite() && timeout.inWholeMilliseconds > 0) {
            "timeout must be finite and at least 1 millisecond"
        }
        expectSuccess = true // Kaster exception for 4xx og 5xx svar

        // Fristen omfatter alle forsøk og ventetiden, men ikke annet arbeid i tilgangssjekken.
        install(totalTimeout(timeout))
        install(HttpRequestRetry) {
            retryIf(maxRetries = MAKS_ANTALL_RETRIES) { _, response ->
                response.status.value in setOf(500, 502, 503, 504)
            }
            retryOnExceptionIf(maxRetries = MAKS_ANTALL_RETRIES) { _, cause ->
                // Ktor kan pakke timeouter i CancellationException, så vi sjekker årsaken.
                // Kansellering fra kalleren avbryter fortsatt kallet og ventetiden før et nytt forsøk.
                val failure = cause.unwrapCancellationException()

                // CIO pakker EOF før responsheaderne i ClosedReadChannelException.
                val wrappedEof = failure is ClosedReadChannelException &&
                        failure.cause is EOFException

                failure is HttpRequestTimeoutException ||
                        failure is ConnectTimeoutException ||
                        failure is SocketTimeoutException ||
                        failure is SocketException ||
                        failure is EOFException ||
                        failure is ClosedChannelException ||
                        wrappedEof
            }
            constantDelay(
                millis = RETRY_DELAY_MS,
                randomizationMs = RETRY_JITTER_MS,
                respectRetryAfterHeader = true
            )
        }

        install(HttpTimeout) {
            requestTimeoutMillis = timeout.inWholeMilliseconds
            socketTimeoutMillis = timeout.inWholeMilliseconds
            connectTimeoutMillis = minOf(CONNECT_TIMEOUT, timeout).inWholeMilliseconds
        }

        install(ContentNegotiation) {
            jackson {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }
}
