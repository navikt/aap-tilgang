package tilgang.auth

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tilgang.TexasConfig
import java.net.URI
import kotlin.test.assertFailsWith

class TexasGatewayTest {
    private val mottatteRequests = mutableListOf<Map<String, String>>()
    private var responseStatus = HttpStatusCode.OK
    private val currentToken = mockk<OidcToken> {
        every { token() } returns "user-token"
    }

    @Test
    fun `bruker konfigurerte endepunkter med riktig request og respons`() = runTest {
        medGateway { gateway ->
            assertThat(gateway.machineToMachineToken("scope-a")).isEqualTo(TexasTokenResponse("m2m-token", 120))
            assertThat(gateway.exchangeToken("scope-b", currentToken)).isEqualTo(TexasTokenResponse("obo-token", 120))
            assertThat(mottatteRequests).containsExactly(
                mapOf("identity_provider" to "entra_id", "target" to "scope-a"),
                mapOf("identity_provider" to "entra_id", "target" to "scope-b", "user_token" to "user-token")
            )
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `lar feil fra begge texas-endepunktene forplante seg`(obo: Boolean) = runTest {
        responseStatus = HttpStatusCode.BadRequest

        medGateway { gateway ->
            val exception = assertFailsWith<ClientRequestException> {
                if (obo) {
                    gateway.exchangeToken("scope", currentToken)
                } else {
                    gateway.machineToMachineToken("scope")
                }
            }
            assertThat(exception.response.status).isEqualTo(HttpStatusCode.BadRequest)
        }
    }

    private suspend fun medGateway(test: suspend (TexasGateway) -> Unit) {
        val server = embeddedServer(Netty, port = 0) {
            install(ContentNegotiation) {
                jackson()
            }
            routing {
                for ((path, token) in mapOf("/custom/token" to "m2m-token", "/other/exchange" to "obo-token")) {
                    post(path) {
                        assertThat(call.request.header(HttpHeaders.Authorization)).isNull()
                        mottatteRequests.add(call.receive<Map<String, String>>())
                        call.respondText(
                            """{"access_token":"$token","expires_in":120}""",
                            ContentType.Application.Json,
                            responseStatus
                        )
                    }
                }
            }
        }.start()

        try {
            val port = server.engine.resolvedConnectors().single().port
            val config = TexasConfig(
                URI("http://localhost:$port/custom/token"),
                URI("http://localhost:$port/other/exchange")
            )
            createTexasClient(config.tokenEndpoint).use { tokenClient ->
                createTexasClient(config.exchangeEndpoint).use { exchangeClient ->
                    test(TexasGateway(tokenClient, exchangeClient))
                }
            }
        } finally {
            server.stop(0, 0)
        }
    }
}
