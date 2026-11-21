package tilgang.integrasjoner

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tilgang.MsGraphConfig
import tilgang.auth.ITokenProvider
import tilgang.integrasjoner.msgraph.Group
import tilgang.integrasjoner.msgraph.MemberOf
import tilgang.integrasjoner.msgraph.MsGraphGateway
import tilgang.integrasjoner.msgraph.createMsGraphClient
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertFailsWith

class MsGraphTest {
    private val tokenProvider = mockk<ITokenProvider>()
    private val redis = mockk<Redis>()
    private val tokenA = mockk<OidcToken>()
    private val tokenB = mockk<OidcToken>()
    private val gruppeA = Group(UUID.randomUUID(), "gruppe-a")
    private val gruppeB = Group(UUID.randomUUID(), "gruppe-b")
    private val cache = ConcurrentHashMap<Key, ByteArray>()
    private val mottatteTokens = ConcurrentLinkedQueue<String>()
    private var responseStatus = HttpStatusCode.OK

    @ParameterizedTest
    @ValueSource(strings = ["", "/", "/v1.0", "/v1.0/"])
    fun `bruker konfigurert url og obo-token og cacher grupper per ansatt`(basePath: String) = runTest {
        medGateway(basePath) { gateway ->
            repeat(2) {
                assertThat(gateway.hentAdGrupper(tokenA, "Z1")).isEqualTo(MemberOf(listOf(gruppeA)))
            }

            assertThat(mottatteTokens).containsExactly("Bearer obo-a")
            assertThat(cache.keys).containsExactly(Key("msgraph", "Z1"))
            assertThat(cache.values.single().deserialize<MemberOf>()).isEqualTo(MemberOf(listOf(gruppeA)))
            coVerify(exactly = 1) { tokenProvider.oboToken("msgraph-test-scope", tokenA) }
            coVerify(exactly = 1) { redis.set(Key("msgraph", "Z1"), any(), 3600) }
        }
    }

    @Test
    fun `holder obo-token og cache adskilt for samtidige brukere`() = runTest {
        medGateway("/v1.0") { gateway ->
            val startetA = CompletableDeferred<Unit>()
            val startetB = CompletableDeferred<Unit>()
            coEvery { tokenProvider.oboToken("msgraph-test-scope", tokenA) } coAnswers {
                startetA.complete(Unit)
                startetB.await()
                "obo-a"
            }
            coEvery { tokenProvider.oboToken("msgraph-test-scope", tokenB) } coAnswers {
                startetB.complete(Unit)
                startetA.await()
                "obo-b"
            }

            coroutineScope {
                val svarA = async { gateway.hentAdGrupper(tokenA, "Z1") }
                val svarB = async { gateway.hentAdGrupper(tokenB, "Z2") }
                assertThat(svarA.await()).isEqualTo(MemberOf(listOf(gruppeA)))
                assertThat(svarB.await()).isEqualTo(MemberOf(listOf(gruppeB)))
            }

            assertThat(gateway.hentAdGrupper(tokenA, "Z1")).isEqualTo(MemberOf(listOf(gruppeA)))
            assertThat(gateway.hentAdGrupper(tokenB, "Z2")).isEqualTo(MemberOf(listOf(gruppeB)))
            assertThat(mottatteTokens).containsExactlyInAnyOrder("Bearer obo-a", "Bearer obo-b")
            coVerify(exactly = 1) { tokenProvider.oboToken("msgraph-test-scope", tokenA) }
            coVerify(exactly = 1) { tokenProvider.oboToken("msgraph-test-scope", tokenB) }
        }
    }

    @Test
    fun `lar http-feil forplante seg uten aa cache svaret`() = runTest {
        responseStatus = HttpStatusCode.Forbidden

        medGateway("") { gateway ->
            val exception = assertFailsWith<ClientRequestException> {
                gateway.hentAdGrupper(tokenA, "Z1")
            }
            assertThat(exception.response.status).isEqualTo(HttpStatusCode.Forbidden)
            coVerify(exactly = 0) { redis.set(any(), any(), any()) }
        }
    }

    private suspend fun medGateway(basePath: String, test: suspend (MsGraphGateway) -> Unit) {
        val server = embeddedServer(Netty, port = 0) {
            routing {
                get("${basePath.trimEnd('/')}/me/memberOf") {
                    assertThat(call.request.queryParameters["\$top"]).isEqualTo("500")
                    assertThat(call.request.queryParameters["\$select"]).isEqualTo("id,mailNickname")
                    val authorization = requireNotNull(call.request.header("Authorization"))
                    mottatteTokens.add(authorization)
                    val gruppe = when (authorization) {
                        "Bearer obo-a" -> gruppeA
                        "Bearer obo-b" -> gruppeB
                        else -> error("Uventet autentisering mot MS Graph")
                    }
                    call.respondText(
                        """{"value":[{"id":"${gruppe.id}","mailNickname":"${gruppe.name}"}]}""",
                        ContentType.Application.Json,
                        responseStatus
                    )
                }
            }
        }.start()
        val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

        try {
            val port = server.engine.resolvedConnectors().single().port
            val config = MsGraphConfig(URI("http://localhost:$port$basePath"), "msgraph-test-scope")
            coEvery { tokenProvider.oboToken("msgraph-test-scope", tokenA) } returns "obo-a"
            coEvery { tokenProvider.oboToken("msgraph-test-scope", tokenB) } returns "obo-b"
            coEvery { redis[any()] } answers { cache[firstArg()] }
            coEvery { redis.set(any(), any(), any()) } answers {
                cache[firstArg()] = secondArg()
            }

            createMsGraphClient(config, tokenProvider).use { client ->
                test(MsGraphGateway(redis, client, prometheus))
            }
        } finally {
            server.stop(0, 0)
            prometheus.close()
        }
    }
}
