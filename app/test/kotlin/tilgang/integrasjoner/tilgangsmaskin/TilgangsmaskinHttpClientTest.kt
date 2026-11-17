package tilgang.integrasjoner.tilgangsmaskin

import io.ktor.client.engine.mock.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.channels.ClosedChannelException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TilgangsmaskinHttpClientTest {
    @Test
    fun `returnerer et tomt 204-svar uten nye forsøk`() = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                attempts++
                val timeouts = requireNotNull(request.getCapabilityOrNull(HttpTimeoutCapability))
                assertThat(timeouts.requestTimeoutMillis).isEqualTo(5_000L)
                assertThat(timeouts.socketTimeoutMillis).isEqualTo(5_000L)
                assertThat(timeouts.connectTimeoutMillis).isEqualTo(2_000L)
                respond("", HttpStatusCode.NoContent)
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                val response = client.post("https://tilgangsmaskin.test")
                assertThat(response.status).isEqualTo(HttpStatusCode.NoContent)
                assertThat(response.bodyAsText()).isEmpty()
                assertThat(attempts).isEqualTo(1)
                assertThat(testScheduler.currentTime).isZero()
            }
        }
    }

    @ParameterizedTest
    @CsvSource(
        "500, 1", "500, 2",
        "502, 1", "502, 2",
        "503, 1", "503, 2",
        "504, 1", "504, 2"
    )
    fun `lykkes etter forbigående serverfeil`(status: Int, failedAttempts: Int) = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                if (attempts <= failedAttempts) respond("unavailable", HttpStatusCode.fromValue(status))
                else respond("", HttpStatusCode.NoContent)
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                assertThat(client.post("https://tilgangsmaskin.test").status).isEqualTo(HttpStatusCode.NoContent)
                assertThat(attempts).isEqualTo(failedAttempts + 1)
                assertThat(testScheduler.currentTime).isBetween(100L * failedAttempts, 250L * failedAttempts)
            }
        }
    }

    @ParameterizedTest
    @MethodSource("recoverableFailures")
    fun `lykkes etter forbigående nettverksfeil`(failure: Exception, failedAttempts: Int) = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                if (attempts <= failedAttempts) throw failure
                respond("", HttpStatusCode.NoContent)
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                assertThat(client.post("https://tilgangsmaskin.test").status).isEqualTo(HttpStatusCode.NoContent)
                assertThat(attempts).isEqualTo(failedAttempts + 1)
                assertThat(testScheduler.currentTime).isBetween(100L * failedAttempts, 250L * failedAttempts)
            }
        }
    }

    @Test
    fun `lar første forsøk bruke hele standardbudsjettet`() = runTest {
        var attempts = 0
        val timeouts = mutableListOf<Long?>()
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                attempts++
                timeouts += request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis
                delay(4_900.milliseconds)
                respond("", HttpStatusCode.NoContent)
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                assertThat(client.post("https://tilgangsmaskin.test").status).isEqualTo(HttpStatusCode.NoContent)
                assertThat(attempts).isEqualTo(1)
                assertThat(timeouts).containsExactly(5_000L)
                assertThat(testScheduler.currentTime).isEqualTo(4_900L)
            }
        }
    }

    @Test
    fun `prøver ikke på nytt når hele tidsbudsjettet er brukt`() = runTest {
        var attempts = 0
        val timeouts = mutableListOf<Long?>()
        val elapsed = mutableListOf<Long>()
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                attempts++
                timeouts += request.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis
                val start = testScheduler.currentTime
                try {
                    awaitCancellation()
                } finally {
                    elapsed += testScheduler.currentTime - start
                }
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                assertFailsWith<HttpRequestTimeoutException> {
                    client.post("https://tilgangsmaskin.test")
                }
                testScheduler.advanceUntilIdle()
                assertThat(attempts).isEqualTo(1)
                assertThat(timeouts).containsExactly(5_000L)
                assertThat(elapsed).containsExactly(5_000L)
                assertThat(testScheduler.currentTime).isEqualTo(5_000L)
            }
        }
    }

    @Test
    fun `lykkes etter timeout ved oppkobling med tiden som gjenstår`() = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                if (attempts == 1) {
                    delay(2.seconds)
                    throw ConnectTimeoutException("connection timed out")
                }
                delay(2_500.milliseconds)
                respond("", HttpStatusCode.NoContent)
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                assertThat(client.post("https://tilgangsmaskin.test").status).isEqualTo(HttpStatusCode.NoContent)
                assertThat(attempts).isEqualTo(2)
                assertThat(testScheduler.currentTime).isBetween(4_600L, 4_750L)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(longs = [500, 8_000])
    fun `overholder det angitte tidsbudsjettet`(timeoutMs: Long) = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                attempts++
                val timeouts = requireNotNull(request.getCapabilityOrNull(HttpTimeoutCapability))
                assertThat(timeouts.requestTimeoutMillis).isEqualTo(timeoutMs)
                assertThat(timeouts.socketTimeoutMillis).isEqualTo(timeoutMs)
                assertThat(timeouts.connectTimeoutMillis).isEqualTo(minOf(2_000L, timeoutMs))
                awaitCancellation()
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine, timeout = timeoutMs.milliseconds).use { client ->
                val failure = assertFailsWith<HttpRequestTimeoutException> {
                    client.post("https://tilgangsmaskin.test")
                }
                assertThat(failure.message).contains("request_timeout=$timeoutMs ms")
                testScheduler.advanceUntilIdle()
                assertThat(attempts).isEqualTo(1)
                assertThat(testScheduler.currentTime).isEqualTo(timeoutMs)
            }
        }
    }

    @Test
    fun `et lengre tidsbudsjett tillater svar etter standardfristen`() = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                delay(6.seconds)
                respond("", HttpStatusCode.NoContent)
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine, timeout = 8.seconds).use { client ->
                assertThat(client.post("https://tilgangsmaskin.test").status).isEqualTo(HttpStatusCode.NoContent)
                assertThat(attempts).isEqualTo(1)
                assertThat(testScheduler.currentTime).isEqualTo(6_000L)
            }
        }
    }

    @Test
    fun `starter et nytt tidsbudsjett for hvert kall med samme klient`() = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                delay(4.seconds)
                respond("", HttpStatusCode.NoContent)
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                repeat(2) {
                    assertThat(client.post("https://tilgangsmaskin.test").status).isEqualTo(HttpStatusCode.NoContent)
                }
                assertThat(attempts).isEqualTo(2)
                assertThat(testScheduler.currentTime).isEqualTo(8_000L)
            }
        }
    }

    @Test
    fun `avviser uendelige tidsbudsjetter og tidsbudsjetter under ett millisekund`() = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                respond("", HttpStatusCode.NoContent)
            }
        }.use { engine ->
            for (timeout in listOf(Duration.ZERO, (-1).seconds, Duration.INFINITE, 500.microseconds)) {
                val failure = assertFailsWith<IllegalArgumentException> {
                    TilgangsmaskinHttpClient.create(engine, timeout = timeout).close()
                }
                assertThat(failure.message).isEqualTo("timeout must be finite and at least 1 millisecond")
            }
            assertThat(attempts).isZero()
        }
    }

    @Test
    fun `kaster den siste serverfeilen videre etter to nye forsøk`() = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                respond("unavailable", HttpStatusCode.ServiceUnavailable)
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                val failure = assertFailsWith<ServerResponseException> {
                    client.post("https://tilgangsmaskin.test")
                }
                assertThat(failure.response.status).isEqualTo(HttpStatusCode.ServiceUnavailable)
                assertThat(attempts).isEqualTo(3)
                assertThat(testScheduler.currentTime).isBetween(200L, 500L)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `kaster den siste nettverksfeilen videre etter to nye forsøk`(serverErrorOnSecondAttempt: Boolean) = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                if (serverErrorOnSecondAttempt && attempts == 2) {
                    respond("unavailable", HttpStatusCode.ServiceUnavailable)
                } else {
                    throw SocketException("connection reset on attempt $attempts")
                }
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                val failure = assertFailsWith<SocketException> {
                    client.post("https://tilgangsmaskin.test")
                }
                assertThat(failure.message).isEqualTo("connection reset on attempt 3")
                assertThat(attempts).isEqualTo(3)
                assertThat(testScheduler.currentTime).isBetween(200L, 500L)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [400, 401, 403, 404, 408, 429])
    fun `prøver ikke på nytt ved klientfeil`(status: Int) = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                respond("rejected", HttpStatusCode.fromValue(status))
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                val failure = assertFailsWith<ClientRequestException> {
                    client.post("https://tilgangsmaskin.test")
                }
                assertThat(failure.response.status.value).isEqualTo(status)
                assertThat(attempts).isEqualTo(1)
                assertThat(testScheduler.currentTime).isZero()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [501, 505])
    fun `prøver ikke på nytt ved permanente serverfeil`(status: Int) = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                respond("unsupported", HttpStatusCode.fromValue(status))
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                val failure = assertFailsWith<ServerResponseException> {
                    client.post("https://tilgangsmaskin.test")
                }
                assertThat(failure.response.status.value).isEqualTo(status)
                assertThat(attempts).isEqualTo(1)
                assertThat(testScheduler.currentTime).isZero()
            }
        }
    }

    @ParameterizedTest
    @MethodSource("permanentFailures")
    fun `prøver ikke på nytt ved TLS-feil, protokollfeil eller uventede feil`(failure: Exception) = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                throw failure
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                val thrown = assertFailsWith<Exception> {
                    client.post("https://tilgangsmaskin.test")
                }
                assertThat(thrown).isExactlyInstanceOf(failure.javaClass).hasMessage(failure.message)
                assertThat(attempts).isEqualTo(1)
                assertThat(testScheduler.currentTime).isZero()
            }
        }
    }

    @Test
    fun `følger Retry-After når det er tid til et nytt forsøk`() = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                if (attempts == 1) {
                    respond("unavailable", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.RetryAfter, "2"))
                } else {
                    respond("", HttpStatusCode.NoContent)
                }
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                assertThat(client.post("https://tilgangsmaskin.test").status).isEqualTo(HttpStatusCode.NoContent)
                assertThat(attempts).isEqualTo(2)
                assertThat(testScheduler.currentTime).isEqualTo(2_000L)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(longs = [500, 5_000])
    fun `totalfristen avbryter venting på Retry-After uten et nytt forsøk`(timeoutMs: Long) = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                respond("unavailable", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.RetryAfter, "60"))
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine, timeout = timeoutMs.milliseconds).use { client ->
                val failure = assertFailsWith<HttpRequestTimeoutException> {
                    client.post("https://tilgangsmaskin.test")
                }
                assertThat(failure.message).contains("request_timeout=$timeoutMs ms")
                assertThat(attempts).isEqualTo(1)
                assertThat(testScheduler.currentTime).isEqualTo(timeoutMs)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(longs = [4_000, 5_000, 8_000])
    fun `totalfristen begrenser både siste forsøk og ventetiden`(timeoutMs: Long) = runTest {
        var attempts = 0
        val started = mutableListOf<Long>()
        val finished = mutableListOf<Long>()
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                started += testScheduler.currentTime
                try {
                    if (attempts <= 2) {
                        delay(500.milliseconds)
                        respond("unavailable", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.RetryAfter, "1"))
                    } else {
                        awaitCancellation()
                    }
                } finally {
                    finished += testScheduler.currentTime
                }
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine, timeout = timeoutMs.milliseconds).use { client ->
                val failure = assertFailsWith<HttpRequestTimeoutException> {
                    client.post("https://tilgangsmaskin.test")
                }
                assertThat(failure.message).contains("request_timeout=$timeoutMs ms")
                testScheduler.advanceUntilIdle()
                assertThat(attempts).isEqualTo(3)
                assertThat(started).containsExactly(0L, 1_500L, 3_000L)
                assertThat(finished).containsExactly(500L, 2_000L, timeoutMs)
                assertThat(testScheduler.currentTime).isEqualTo(timeoutMs)
            }
        }
    }

    @Test
    fun `totalfristen avbryter ventetiden før tredje forsøk`() = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                val retryAfter = if (attempts == 1) "1" else "60"
                respond("unavailable", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.RetryAfter, retryAfter))
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                assertFailsWith<HttpRequestTimeoutException> {
                    client.post("https://tilgangsmaskin.test")
                }
                assertThat(attempts).isEqualTo(2)
                assertThat(testScheduler.currentTime).isEqualTo(5_000L)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [200, 403, 503])
    fun `totalfristen gjelder også mottak av responsinnhold etter et nytt forsøk`(status: Int) = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                if (attempts == 1) {
                    respond("unavailable", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.RetryAfter, "3"))
                } else {
                    val body = ByteChannel()
                    backgroundScope.launch {
                        delay(3.seconds)
                        body.writeStringUtf8("response")
                        body.flushAndClose()
                    }
                    respond(body, HttpStatusCode.fromValue(status))
                }
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                assertFailsWith<HttpRequestTimeoutException> {
                    client.post("https://tilgangsmaskin.test").bodyAsText()
                }
                assertThat(attempts).isEqualTo(2)
                assertThat(testScheduler.currentTime).isEqualTo(5_000L)
            }
        }
    }

    @Test
    fun `prøver ikke på nytt ved kansellering`() = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                throw CancellationException("request cancelled")
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                val failure = assertFailsWith<CancellationException> {
                    client.post("https://tilgangsmaskin.test")
                }
                assertThat(failure.message).isEqualTo("request cancelled")
                assertThat(attempts).isEqualTo(1)
            }
        }
    }

    @Test
    fun `prøver ikke på nytt når kalleren kansellerer et pågående kall`() = runTest {
        var attempts = 0
        val started = CompletableDeferred<Unit>()
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                started.complete(Unit)
                awaitCancellation()
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                val request = async { client.post("https://tilgangsmaskin.test") }
                started.await()
                request.cancel(CancellationException("caller cancelled"))

                val failure = assertFailsWith<CancellationException> { request.await() }
                assertThat(failure.message).isEqualTo("caller cancelled")
                testScheduler.advanceUntilIdle()
                assertThat(attempts).isEqualTo(1)
            }
        }
    }

    @Test
    fun `prøver ikke på nytt eller endrer feilen når kallerens kortere frist utløper`() = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                awaitCancellation()
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                assertFailsWith<TimeoutCancellationException> {
                    withTimeout(500.milliseconds) {
                        client.post("https://tilgangsmaskin.test")
                    }
                }
                assertThat(attempts).isEqualTo(1)
                assertThat(testScheduler.currentTime).isEqualTo(500L)
            }
        }
    }

    @Test
    fun `kansellering fra kalleren avbryter ventetiden mellom forsøk`() = runTest {
        var attempts = 0
        MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                attempts++
                respond("unavailable", HttpStatusCode.ServiceUnavailable)
            }
        }.use { engine ->
            TilgangsmaskinHttpClient.create(engine).use { client ->
                val request = async { client.post("https://tilgangsmaskin.test") }
                testScheduler.advanceTimeBy(50)
                assertThat(attempts).isEqualTo(1)
                request.cancel(CancellationException("caller cancelled during backoff"))
                val failure = assertFailsWith<CancellationException> { request.await() }
                assertThat(failure.message).isEqualTo("caller cancelled during backoff")
                testScheduler.advanceUntilIdle()
                assertThat(attempts).isEqualTo(1)
            }
        }
    }

    companion object {
        @JvmStatic
        fun recoverableFailures(): List<Arguments> = listOf(
            SocketException("connection reset"),
            ConnectException("connection refused"),
            EOFException("unexpected end of stream"),
            ClosedChannelException(),
            SocketTimeoutException("socket timed out"),
            ConnectTimeoutException("connection timed out")
        ).flatMap { failure ->
            (1..2).map { failedAttempts -> Arguments.of(failure, failedAttempts) }
        }

        @JvmStatic
        fun permanentFailures(): List<Exception> = listOf(
            SSLHandshakeException("certificate rejected"),
            ProtocolException("invalid HTTP response"),
            IOException("unclassified I/O failure"),
            IllegalArgumentException("invalid request"),
            IllegalStateException("unexpected state")
        )
    }
}
