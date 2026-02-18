package tilgang

import com.nimbusds.jwt.SignedJWT
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.aap.tilgang.Rolle
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.*

class TilgangApiTest {
    companion object {
        private val server = MockOAuth2Server()
        private val redis = InitTestRedis

        @BeforeAll
        @JvmStatic
        fun setup() {
            server.start()

            System.setProperty(
                "azure.openid.config.token.endpoint",
                server.tokenEndpointUrl("default").toString()
            )
            System.setProperty("azure.app.client.id", "default")
            System.setProperty("azure.app.client.secret", "default")
            System.setProperty("azure.openid.config.jwks.uri", server.jwksUrl("default").toString())
            System.setProperty("azure.openid.config.issuer", server.issuerUrl("default").toString())


            System.setProperty("pdl.base.url", "http://localhost")
            System.setProperty("pdl.scope", "pdl")

            // TODO: Lag fakes for disse
            System.setProperty("saf.base.url", "test")
            System.setProperty("saf.scope", "saf")
            System.setProperty("nom.scope", "nom")
            System.setProperty("nom.base.url", "test")
            System.setProperty("skjerming.scope", "skjerming")
            System.setProperty("skjerming.base.url", "test")
            System.setProperty("behandlingsflyt.scope", "behandlingsflyt")
            System.setProperty("behandlingsflyt.base.url", "test")
            System.setProperty("ms.graph.scope", "msgraph")
            System.setProperty("ms.graph.base.url", "test")
            System.setProperty("integrasjon.tilgangsmaskin.scope", "tilgangsmaskin")
            System.setProperty("integrasjon.tilgangsmaskin.url", "tilgangsmaskin")
        }

        @AfterAll
        @JvmStatic
        fun afterAll() {
            server.shutdown()
        }
    }

    @Test
    fun `kan hente ut roller fra claims`() {
        val beslutterUUID = UUID.randomUUID()
        val alleRoller = listOf(
            Role(Rolle.SAKSBEHANDLER_OPPFOLGING, UUID.randomUUID()),
            Role(Rolle.SAKSBEHANDLER_NASJONAL, UUID.randomUUID()),
            Role(Rolle.BESLUTTER, beslutterUUID),
            Role(Rolle.LES, UUID.randomUUID()),
            Role(Rolle.DRIFT, UUID.randomUUID()),
            Role(Rolle.PRODUKSJONSSTYRING, UUID.randomUUID()),
            Role(Rolle.KVALITETSSIKRER, UUID.randomUUID())
        )
        testApplication {
            application {
                api(
                    Config(
                        roles = alleRoller,
                        redis = RedisConfig(
                            uri = redis.uri,
                            username = "test",
                            password = "test"
                        )
                    )
                )
            }

            val jwt = issueToken(listOf(beslutterUUID.toString()))

            val response = sendGetRequest(client, jwt, "/roller")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.bodyAsText()).isEqualTo("[\"BESLUTTER\"]")
        }
    }

    private suspend fun sendGetRequest(
        client: HttpClient,
        jwt: SignedJWT,
        path: String
    ) = client.get(path) {
        header("Authorization", "Bearer ${jwt.serialize()}")
        header("X-callid", UUID.randomUUID().toString())
        contentType(ContentType.Application.Json)
    }

    private fun issueToken(groups: List<String>) = server.issueToken(
        issuerId = "default",
        claims = mapOf(
            "groups" to groups,
        ),
    )
}