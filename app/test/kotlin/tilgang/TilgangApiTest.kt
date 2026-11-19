package tilgang

import com.nimbusds.jwt.SignedJWT
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.util.UUID
import no.nav.aap.komponenter.json.DefaultJsonMapper
import no.nav.aap.tilgang.Rolle
import no.nav.aap.tilgang.TilgangResponse
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tilgang.fakes.Fakes
import tilgang.fakes.WithFakes

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithFakes
class TilgangApiTest {
    private val oAuth2Server = MockOAuth2Server()

    @BeforeAll
    fun setup() {
        oAuth2Server.start()
    }

    @AfterAll
    fun tearDown() {
        oAuth2Server.shutdown()
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
                        redis = Fakes.getRedisConfig(),
                    ),
                    redis = Fakes.getRedisServer()
                )
            }

            val jwt = issueToken(listOf(beslutterUUID.toString()))

            val response = sendGetRequest(client, jwt, "/roller")

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            assertThat(response.bodyAsText()).isEqualTo("[\"BESLUTTER\"]")
        }
    }

    @ParameterizedTest
    @CsvSource(
        "LES, 12300000000, true",
        "SAKSBEHANDLER_OPPFOLGING, 12300000000, true",
        "DRIFT, 12300000000, false",
        "LES, 45678901000, false"
    )
    fun `sjekker tilgang til arenasaker med standardverdier`(
        rolle: Rolle,
        personIdent: String,
        forventetTilgang: Boolean
    ) {
        val gruppeId = UUID.randomUUID()
        testApplication {
            application {
                api(
                    Config(
                        roles = listOf(Role(rolle, gruppeId)),
                        redis = Fakes.getRedisConfig(),
                    ),
                    redis = Fakes.getRedisServer()
                )
            }

            val response = client.post("/tilgang/arenasaker") {
                bearerAuth(issueToken(listOf(gruppeId.toString())).serialize())
                header("Nav-CallId", UUID.randomUUID().toString())
                contentType(ContentType.Application.Json)
                setBody("""{"personIdent":"$personIdent"}""")
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val tilgang = DefaultJsonMapper.objectMapper().readValue<TilgangResponse>(response.bodyAsText())
            assertThat(tilgang.tilgang).isEqualTo(forventetTilgang)
        }
    }

    @Test
    fun `nekter tilgang til arenasaker uten roller`() {
        testApplication {
            application {
                api(
                    Config(roles = emptyList(), redis = Fakes.getRedisConfig()),
                    redis = Fakes.getRedisServer()
                )
            }

            val response = client.post("/tilgang/arenasaker") {
                bearerAuth(issueToken(emptyList()).serialize())
                contentType(ContentType.Application.Json)
                setBody("""{"personIdent":"12345678901","operasjon":"SE"}""")
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val tilgang = DefaultJsonMapper.objectMapper().readValue<TilgangResponse>(response.bodyAsText())
            assertThat(tilgang.tilgang).isFalse()
        }
    }

    @ParameterizedTest
    @MethodSource("requestBoundaryCases")
    fun `bevarer API-felter og standardverdier ved oversetting til serviceinput`(
        ressurs: String,
        payload: String,
        forventetTilgang: Boolean,
    ) {
        val gruppeId = UUID.randomUUID()
        testApplication {
            application {
                api(
                    Config(
                        roles = listOf(Role(Rolle.LES, gruppeId)),
                        redis = Fakes.getRedisConfig(),
                    ),
                    redis = Fakes.getRedisServer()
                )
            }

            val response = client.post("/tilgang/$ressurs") {
                bearerAuth(issueToken(listOf(gruppeId.toString())).serialize())
                header("Nav-CallId", UUID.randomUUID().toString())
                contentType(ContentType.Application.Json)
                setBody(payload)
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            val tilgang = DefaultJsonMapper.objectMapper().readValue<TilgangResponse>(response.bodyAsText())
            assertThat(tilgang.tilgang).isEqualTo(forventetTilgang)
        }
    }

    private fun requestBoundaryCases(): List<Arguments> {
        val referanse = UUID.randomUUID()
        return listOf(
            Arguments.of(
                "sak",
                """
                    {
                      "saksnummer": "sak-1",
                      "påkrevdRolle": null,
                      "operasjon": "SE",
                      "relevanteIdenter": {"søker": ["45678901000"], "barn": []}
                    }
                """.trimIndent(),
                false,
            ),
            Arguments.of(
                "behandling",
                """
                    {
                      "behandlingsreferanse": "$referanse",
                      "påkrevdRolle": ["LES"],
                      "operasjon": "SAKSBEHANDLE",
                      "relevanteIdenter": {"søker": ["12300000000"], "barn": []},
                      "operasjonerIKontekst": ["SE"]
                    }
                """.trimIndent(),
                true,
            ),
            Arguments.of(
                "journalpost",
                """{"journalpostId":123,"påkrevdRolle":["LES"],"operasjon":"SAKSBEHANDLE"}""",
                false,
            ),
            Arguments.of(
                "arenasaker",
                """{"personIdent":"12300000000"}""",
                true,
            ),
            Arguments.of(
                "arenasaker",
                """{"personIdent":"12300000000","påkrevdRolle":[],"operasjon":"DRIFTE"}""",
                false,
            ),
            Arguments.of(
                "person",
                """{"personIdent":"12300000000","påkrevdRolle":null}""",
                true,
            ),
            Arguments.of(
                "person",
                """{"personIdent":"12300000000","operasjon":"DRIFTE"}""",
                false,
            ),
            Arguments.of(
                "tilbakekreving",
                """
                    {
                      "saksnummer": "sak-1",
                      "behandlingsreferanse": "$referanse",
                      "påkrevdRoller": ["LES"],
                      "operasjon": "SAKSBEHANDLE"
                    }
                """.trimIndent(),
                false,
            ),
        )
    }

    private suspend fun sendGetRequest(
        client: HttpClient,
        jwt: SignedJWT,
        path: String,
    ) = client.get(path) {
        bearerAuth(jwt.serialize())
        header("X-callid", UUID.randomUUID().toString())
        contentType(ContentType.Application.Json)
    }

    private fun issueToken(groups: List<String>) = oAuth2Server.issueToken(
        issuerId = "default",
        claims = mapOf(
            "groups" to groups,
            "NAVident" to "Z990883",
        ),
    )
}