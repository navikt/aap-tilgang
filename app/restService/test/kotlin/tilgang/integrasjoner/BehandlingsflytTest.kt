package tilgang.integrasjoner

import io.ktor.http.ContentType
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
import kotlinx.coroutines.test.runTest
import no.nav.aap.tilgang.RelevanteIdenter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tilgang.BehandlingsflytConfig
import tilgang.auth.ITokenProvider
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytGateway
import tilgang.integrasjoner.behandlingsflyt.createBehandlingsflytClient
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import java.net.URI

class BehandlingsflytTest {
    @ParameterizedTest
    @ValueSource(strings = ["", "/", "/prefiks", "/prefiks/"])
    fun `bruker konfigurert base-url og token for begge endepunkter og cacher svar`(basePath: String) = runTest {
        val mottatteStier = mutableListOf<String>()
        val server = embeddedServer(Netty, port = 0) {
            routing {
                for (ressurs in listOf("sak", "behandling")) {
                    get("${basePath.trimEnd('/')}/pip/api/$ressurs/{referanse}/identer") {
                        assertThat(call.request.header("Authorization")).isEqualTo("Bearer test-token")
                        mottatteStier.add("$ressurs/${call.parameters["referanse"]}")
                        call.respondText(
                            """{"søker":["123"],"barn":["456"]}""",
                            ContentType.Application.Json
                        )
                    }
                }
            }
        }.start()
        val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

        try {
            val port = server.engine.resolvedConnectors().single().port
            val config = BehandlingsflytConfig(URI("http://localhost:$port$basePath"), "test-scope")
            val tokenProvider = mockk<ITokenProvider>()
            coEvery { tokenProvider.m2mToken("test-scope") } returns "test-token"

            val cache = mutableMapOf<Key, ByteArray>()
            val redis = mockk<Redis>()
            coEvery { redis[any()] } answers { cache[firstArg()] }
            coEvery { redis.set(any(), any(), any()) } answers {
                cache[firstArg()] = secondArg()
            }

            createBehandlingsflytClient(config, tokenProvider).use { client ->
                val gateway = BehandlingsflytGateway(redis, client, prometheus)
                val forventet = RelevanteIdenter(listOf("123"), listOf("456"))

                repeat(2) {
                    assertThat(gateway.hentIdenterForSak("S1")).isEqualTo(forventet)
                    assertThat(gateway.hentIdenterForBehandling("B1")).isEqualTo(forventet)
                }

                assertThat(mottatteStier).containsExactly("sak/S1", "behandling/B1")
                assertThat(cache.keys).containsExactlyInAnyOrder(
                    Key("identer_sak", "S1"),
                    Key("identer_behandling", "B1")
                )
                assertThat(cache.values.map { it.deserialize<RelevanteIdenter>() })
                    .containsOnly(forventet)
                coVerify(exactly = 2) { tokenProvider.m2mToken("test-scope") }
                coVerify(exactly = 2) { redis.set(any(), any(), 3600) }
            }
        } finally {
            server.stop(0, 0)
            prometheus.close()
        }
    }
}
