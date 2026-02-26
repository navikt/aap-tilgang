package tilgang

import com.nimbusds.jwt.SignedJWT
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.util.UUID
import no.nav.aap.tilgang.Rolle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tilgang.fakes.Fakes
import tilgang.fakes.WithFakes

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithFakes
class TilgangApiTest {
    private val oAuth2Server = Fakes.getOAuth2Server()

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
                        redis = Fakes.getRedisConfig()
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
        path: String,
    ) = client.get(path) {
        header("Authorization", "Bearer ${jwt.serialize()}")
        header("X-callid", UUID.randomUUID().toString())
        contentType(ContentType.Application.Json)
    }

    private fun issueToken(groups: List<String>) = oAuth2Server.issueToken(
        issuerId = "default",
        claims = mapOf(
            "groups" to groups,
        ),
    )
}