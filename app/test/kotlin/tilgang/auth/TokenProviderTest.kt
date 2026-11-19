package tilgang.auth

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.Clock
import java.time.Instant
import kotlin.test.assertFailsWith

class TokenProviderTest {
    private val texasGateway = mockk<TexasGateway>()
    private val prometheus = SimpleMeterRegistry()
    private var now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = mockk<Clock> {
        every { instant() } answers { now }
    }
    private val tokenProvider = TokenProvider(texasGateway, prometheus, clock)
    private val currentToken = mockk<OidcToken> {
        every { token() } returns "user-a"
    }

    @AfterEach
    fun close() {
        tokenProvider.close()
        prometheus.close()
    }

    @ParameterizedTest
    @CsvSource(value = ["60, 30", "null, 30", "20, 1"], nullValues = ["null"])
    fun `fornyer token ved utlop med sikkerhetsmargin og fallback`(expiresIn: Long?, ttl: Long) = runTest {
        coEvery { texasGateway.machineToMachineToken("scope") } returnsMany listOf(
            TexasTokenResponse("first-token", expiresIn),
            TexasTokenResponse("next-token", expiresIn)
        )

        assertThat(tokenProvider.m2mToken("scope")).isEqualTo("first-token")
        now = now.plusSeconds(ttl - 1)
        assertThat(tokenProvider.m2mToken("scope")).isEqualTo("first-token")
        now = now.plusSeconds(1)
        assertThat(tokenProvider.m2mToken("scope")).isEqualTo("next-token")
        coVerify(exactly = 2) { texasGateway.machineToMachineToken("scope") }
    }

    @Test
    fun `cacher per scope uten aa dele cache mellom instanser`() = runTest {
        coEvery { texasGateway.machineToMachineToken(any()) } coAnswers {
            TexasTokenResponse(firstArg(), 3600)
        }

        repeat(2) {
            assertThat(tokenProvider.m2mToken("scope-a")).isEqualTo("scope-a")
            assertThat(tokenProvider.m2mToken("scope-b")).isEqualTo("scope-b")
        }

        val otherRegistry = SimpleMeterRegistry()
        try {
            TokenProvider(texasGateway, otherRegistry, clock).use { other ->
                assertThat(other.m2mToken("scope-a")).isEqualTo("scope-a")
            }
        } finally {
            otherRegistry.close()
        }
        coVerify(exactly = 2) { texasGateway.machineToMachineToken("scope-a") }
        coVerify(exactly = 1) { texasGateway.machineToMachineToken("scope-b") }
    }

    @Test
    fun `cacher obo-token per scope og innholdet i brukertokenet`() = runTest {
        val sameToken = mockk<OidcToken> {
            every { token() } returns "user-a"
        }
        val otherToken = mockk<OidcToken> {
            every { token() } returns "user-b"
        }
        coEvery { texasGateway.exchangeToken("scope-a", currentToken) } returns TexasTokenResponse("obo-a", 3600)
        coEvery { texasGateway.exchangeToken("scope-a", otherToken) } returns TexasTokenResponse("obo-b", 3600)
        coEvery { texasGateway.exchangeToken("scope-b", currentToken) } returns TexasTokenResponse("obo-c", 3600)

        assertThat(tokenProvider.oboToken("scope-a", currentToken)).isEqualTo("obo-a")
        assertThat(tokenProvider.oboToken("scope-a", sameToken)).isEqualTo("obo-a")
        assertThat(tokenProvider.oboToken("scope-a", otherToken)).isEqualTo("obo-b")
        assertThat(tokenProvider.oboToken("scope-b", currentToken)).isEqualTo("obo-c")
        coVerify(exactly = 3) { texasGateway.exchangeToken(any(), any()) }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `deler samtidige tokenkall selv om en av venterne kanselleres`(obo: Boolean) = runTest {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<TexasTokenResponse>()
        coEvery { texasGateway.machineToMachineToken("scope") } coAnswers {
            started.complete(Unit)
            response.await()
        }
        coEvery { texasGateway.exchangeToken("scope", currentToken) } coAnswers {
            started.complete(Unit)
            response.await()
        }
        suspend fun hentToken() = if (obo) {
            tokenProvider.oboToken("scope", currentToken)
        } else {
            tokenProvider.m2mToken("scope")
        }

        val first = async { hentToken() }
        val second = async { hentToken() }
        started.await()
        first.cancelAndJoin()
        response.complete(TexasTokenResponse("shared-token", 3600))

        assertThat(second.await()).isEqualTo("shared-token")
        if (obo) {
            coVerify(exactly = 1) { texasGateway.exchangeToken("scope", currentToken) }
        } else {
            coVerify(exactly = 1) { texasGateway.machineToMachineToken("scope") }
        }
    }

    @Test
    fun `close kansellerer paagaaende tokenhenting`() = runTest {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        coEvery { texasGateway.machineToMachineToken("scope") } coAnswers {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }

        val request = async { tokenProvider.m2mToken("scope") }
        started.await()
        tokenProvider.close()

        assertFailsWith<CancellationException> { request.await() }
        cancelled.await()
    }

    @Test
    fun `close hindrer nye kall og bruk av cached token`() = runTest {
        coEvery { texasGateway.machineToMachineToken("scope") } returns TexasTokenResponse("cached-token", 3600)
        tokenProvider.m2mToken("scope")

        tokenProvider.close()

        assertFailsWith<CancellationException> { tokenProvider.m2mToken("scope") }
        assertFailsWith<CancellationException> { tokenProvider.oboToken("scope", currentToken) }
        coVerify(exactly = 1) { texasGateway.machineToMachineToken(any()) }
        coVerify(exactly = 0) { texasGateway.exchangeToken(any(), any()) }
    }

    @Test
    fun `lar feil forplante seg og prover paa nytt ved neste kall`() = runTest {
        var attempts = 0
        coEvery { texasGateway.machineToMachineToken("scope") } coAnswers {
            if (attempts++ == 0) {
                error("Feil fra Texas")
            }
            TexasTokenResponse("recovered-token", 3600)
        }

        assertFailsWith<IllegalStateException> { tokenProvider.m2mToken("scope") }
        assertThat(tokenProvider.m2mToken("scope")).isEqualTo("recovered-token")
        coVerify(exactly = 2) { texasGateway.machineToMachineToken("scope") }
    }
}
