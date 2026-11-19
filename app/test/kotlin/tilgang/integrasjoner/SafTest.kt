package tilgang.integrasjoner

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tilgang.SafConfig
import tilgang.auth.ITokenProvider
import tilgang.integrasjoner.saf.Bruker
import tilgang.integrasjoner.saf.BrukerIdType
import tilgang.integrasjoner.saf.SafException
import tilgang.integrasjoner.saf.SafGraphqlGateway
import tilgang.integrasjoner.saf.SafJournalpost
import tilgang.integrasjoner.saf.SafRequest
import tilgang.integrasjoner.saf.createSafClient
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import java.net.URI
import kotlin.test.assertFailsWith

class SafTest {
    private val tokenProvider = mockk<ITokenProvider>()
    private val redis = mockk<Redis>()
    private val cache = mutableMapOf<Key, ByteArray>()
    private val mottatteSpørringer = mutableListOf<SafRequest>()
    private var responseStatus = HttpStatusCode.OK
    private var responseBody = """
        {"data":{"journalpost":{"journalpostId":123,"bruker":{"id":"123","type":"FNR"}}}}
    """.trimIndent()

    @ParameterizedTest
    @ValueSource(strings = ["/graphql", "/prefiks/graphql", "/prefiks/graphql/"])
    fun `bruker konfigurert graphql-url og token og cacher journalpost`(path: String) = runTest {
        medGateway(path) { gateway ->
            val forventet = SafJournalpost(123, bruker = Bruker("123", BrukerIdType.FNR))

            repeat(2) {
                assertThat(gateway.hentJournalpostInfo(123, "call-id")).isEqualTo(forventet)
            }

            assertThat(mottatteSpørringer).containsExactly(SafRequest.hentJournalpost(123))
            assertThat(cache.keys).containsExactly(Key("journalpost", "123"))
            assertThat(cache.values.single().deserialize<SafJournalpost>()).isEqualTo(forventet)
            coVerify(exactly = 1) { tokenProvider.m2mToken("saf-test-scope") }
            coVerify(exactly = 1) { redis.set(Key("journalpost", "123"), any(), 3600) }
        }
    }

    @Test
    fun `kaster graphql-feil uten aa cache svaret`() = runTest {
        responseBody = """
            {
              "data": null,
              "errors": [{
                "message": "Feil fra SAF",
                "locations": [],
                "path": null,
                "extensions": {"code": "FEIL", "classification": "DataFetchingException"}
              }]
            }
        """.trimIndent()

        medGateway("/graphql") { gateway ->
            repeat(2) {
                val exception = assertFailsWith<SafException> {
                    gateway.hentJournalpostInfo(123, "call-id")
                }
                assertThat(exception).hasMessageContaining("Feil fra SAF")
            }
            assertThat(mottatteSpørringer).hasSize(2)
            coVerify(exactly = 0) { redis.set(any(), any(), any()) }
        }
    }

    @Test
    fun `lar http-feil fra SAF forplante seg uten aa cache svaret`() = runTest {
        responseStatus = HttpStatusCode.BadRequest

        medGateway("/graphql") { gateway ->
            val exception = assertFailsWith<ClientRequestException> {
                gateway.hentJournalpostInfo(123, "call-id")
            }
            assertThat(exception.response.status).isEqualTo(HttpStatusCode.BadRequest)
            assertThat(mottatteSpørringer).hasSize(1)
            coVerify(exactly = 0) { redis.set(any(), any(), any()) }
        }
    }

    private suspend fun medGateway(path: String, test: suspend (SafGraphqlGateway) -> Unit) {
        val server = embeddedServer(Netty, port = 0) {
            install(ContentNegotiation) {
                jackson()
            }
            routing {
                post(path) {
                    assertThat(call.request.header("Authorization")).isEqualTo("Bearer saf-test-token")
                    assertThat(call.request.header("Nav-Call-Id")).isEqualTo("call-id")
                    mottatteSpørringer.add(call.receive<SafRequest>())
                    call.respondText(responseBody, ContentType.Application.Json, responseStatus)
                }
            }
        }.start()
        val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

        try {
            val port = server.engine.resolvedConnectors().single().port
            val config = SafConfig(URI("http://localhost:$port$path"), "saf-test-scope")
            coEvery { tokenProvider.m2mToken("saf-test-scope") } returns "saf-test-token"
            coEvery { redis[any()] } answers { cache[firstArg()] }
            coEvery { redis.set(any(), any(), any()) } answers {
                cache[firstArg()] = secondArg()
            }

            createSafClient(config, tokenProvider).use { client ->
                test(SafGraphqlGateway(redis, client, prometheus))
            }
        } finally {
            server.stop(0, 0)
            prometheus.close()
        }
    }
}
