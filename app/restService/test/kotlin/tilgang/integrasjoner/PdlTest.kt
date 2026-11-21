package tilgang.integrasjoner

import io.ktor.http.ContentType
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
import tilgang.PdlConfig
import tilgang.auth.ITokenProvider
import tilgang.integrasjoner.pdl.Gradering
import tilgang.integrasjoner.pdl.HentGeografiskTilknytningResult
import tilgang.integrasjoner.pdl.PdlException
import tilgang.integrasjoner.pdl.PdlGeoType
import tilgang.integrasjoner.pdl.PdlGraphQLGateway
import tilgang.integrasjoner.pdl.PdlRequest
import tilgang.integrasjoner.pdl.PersonResultat
import tilgang.integrasjoner.pdl.createPdlClient
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize
import java.net.URI
import kotlin.test.assertFailsWith

class PdlTest {
    private val tokenProvider = mockk<ITokenProvider>()
    private val redis = mockk<Redis>()
    private val cache = mutableMapOf<Key, ByteArray>()
    private val mottatteSpørringer = mutableListOf<PdlRequest>()
    private var feilRespons: String? = null
    private val person = PersonResultat("456", listOf(Gradering.FORTROLIG), "ok")
    private val geografiskTilknytning = HentGeografiskTilknytningResult(
        gtType = PdlGeoType.KOMMUNE,
        gtKommune = "3207",
        gtBydel = null,
        gtLand = null
    )

    @ParameterizedTest
    @ValueSource(strings = ["/graphql", "/prefiks/graphql", "/prefiks/graphql/"])
    fun `bruker konfigurert url og token for begge sporringer og cacher svar`(path: String) = runTest {
        medGateway(path) { gateway ->
            repeat(2) {
                assertThat(gateway.hentPersonBolk(listOf("456"), "call-id")).containsExactly(person)
                assertThat(gateway.hentGeografiskTilknytning("456", "call-id")).isEqualTo(geografiskTilknytning)
            }

            assertThat(mottatteSpørringer).containsExactly(
                PdlRequest.hentPersonBolk(listOf("456")),
                PdlRequest.hentGeografiskTilknytning("456")
            )
            assertThat(cache[Key("personBolk", "456")]?.deserialize<PersonResultat>()).isEqualTo(person)
            assertThat(cache[Key("geografiskTilknytning", "456")]?.deserialize<HentGeografiskTilknytningResult>())
                .isEqualTo(geografiskTilknytning)
            coVerify(exactly = 2) { tokenProvider.m2mToken("pdl-test-scope") }
            coVerify(exactly = 2) { redis.set(any(), any(), 3600) }
        }
    }

    @Test
    fun `henter bare manglende personer ved delvis cachetreff`() = runTest {
        val cachedPerson = PersonResultat("123", listOf(Gradering.UGRADERT), "ok")
        cache[Key("personBolk", "123")] = cachedPerson.serialize()

        medGateway("/graphql") { gateway ->
            assertThat(gateway.hentPersonBolk(listOf("123", "456"), "call-id"))
                .containsExactly(person, cachedPerson)
            assertThat(mottatteSpørringer).containsExactly(PdlRequest.hentPersonBolk(listOf("456")))
            coVerify(exactly = 1) { redis.set(Key("personBolk", "456"), any(), 3600) }
            coVerify(exactly = 0) { redis.set(Key("personBolk", "123"), any(), any()) }
        }
    }

    @Test
    fun `kaster graphql-feil uten aa cache svaret`() = runTest {
        feilRespons = """
            {
              "data": null,
              "errors": [{
                "message": "Feil fra PDL",
                "locations": [],
                "path": null,
                "extensions": {"code": "FEIL", "classification": "DataFetchingException"}
              }]
            }
        """.trimIndent()

        medGateway("/graphql") { gateway ->
            repeat(2) {
                val exception = assertFailsWith<PdlException> {
                    gateway.hentPersonBolk(listOf("456"), "call-id")
                }
                assertThat(exception).hasMessageContaining("Feil fra PDL")
            }
            assertThat(mottatteSpørringer).hasSize(2)
            coVerify(exactly = 0) { redis.set(any(), any(), any()) }
        }
    }

    private suspend fun medGateway(path: String, test: suspend (PdlGraphQLGateway) -> Unit) {
        val server = embeddedServer(Netty, port = 0) {
            install(ContentNegotiation) {
                jackson()
            }
            routing {
                post(path) {
                    assertThat(call.request.header("Authorization")).isEqualTo("Bearer pdl-test-token")
                    assertThat(call.request.header("Nav-Call-Id")).isEqualTo("call-id")
                    assertThat(call.request.header("TEMA")).isEqualTo("AAP")
                    assertThat(call.request.header("Behandlingsnummer")).isEqualTo("B287")
                    val query = call.receive<PdlRequest>()
                    mottatteSpørringer.add(query)
                    val response = feilRespons ?: if (query.variables.ident != null) {
                        """
                            {"data":{"hentGeografiskTilknytning":{"gtType":"KOMMUNE","gtKommune":"3207"}}}
                        """.trimIndent()
                    } else {
                        """
                            {"data":{"hentPersonBolk":[{
                              "ident":"456",
                              "person":{"adressebeskyttelse":[{"gradering":"FORTROLIG"}]},
                              "code":"ok"
                            }]}}
                        """.trimIndent()
                    }
                    call.respondText(response, ContentType.Application.Json)
                }
            }
        }.start()
        val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

        try {
            val port = server.engine.resolvedConnectors().single().port
            val config = PdlConfig(URI("http://localhost:$port$path"), "pdl-test-scope")
            coEvery { tokenProvider.m2mToken("pdl-test-scope") } returns "pdl-test-token"
            coEvery { redis[any()] } answers { cache[firstArg()] }
            coEvery { redis.set(any(), any(), any()) } answers {
                cache[firstArg()] = secondArg()
            }

            createPdlClient(config, tokenProvider).use { client ->
                test(PdlGraphQLGateway(redis, client, prometheus))
            }
        } finally {
            server.stop(0, 0)
            prometheus.close()
        }
    }
}
