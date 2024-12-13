import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.model.info.InfoModel
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import no.nav.aap.komponenter.server.AZURE
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.DefaultResponseHandler
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.put
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.request.PutRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.NoTokenTokenProvider
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.OnBehalfOfTokenProvider
import no.nav.aap.komponenter.server.commonKtorModule
import no.nav.aap.tilgang.*
import no.nav.aap.tilgang.plugin.kontrakt.Behandlingsreferanse
import no.nav.aap.tilgang.plugin.kontrakt.Journalpostreferanse
import no.nav.aap.tilgang.plugin.kontrakt.Saksreferanse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tilgang.Operasjon
import java.net.URI
import java.util.*
import kotlin.random.Random

class TilgangPluginTest {
    companion object {
        private val fakes = Fakes(azurePort = 8081)

        private val clientForClientCredentials = RestClient(
            config = ClientConfig(scope = "behandlingsflyt"),
            tokenProvider = ClientCredentialsTokenProvider,
            responseHandler = DefaultResponseHandler()
        )
        private val clientForOBO = RestClient.withDefaultResponseHandler(
            config = ClientConfig(scope = "behandlingsflyt"),
            tokenProvider = OnBehalfOfTokenProvider,
        )

        private var oboToken: OidcToken? = null
        private fun getOboToken(): OidcToken {
            val client = RestClient(
                config = ClientConfig(scope = "behandlingsflyt"),
                tokenProvider = NoTokenTokenProvider(),
                responseHandler = DefaultResponseHandler()
            )
            return oboToken ?: OidcToken(
                client.post<Unit, TestToken>(
                    URI.create(requiredConfigForKey("azure.openid.config.token.endpoint")),
                    PostRequest(Unit)
                )!!.access_token
            )
        }

        class Saksinfo(val saksnummer: UUID) : Saksreferanse {
            override fun hentSaksreferanse(): String {
                return saksnummer.toString()
            }
        }

        class Behandlingsinfo(val behandlingsnummer: UUID) : Behandlingsreferanse {
            override fun hentBehandlingsreferanse(): String {
                return behandlingsnummer.toString()
            }

            override fun hentAvklaringsbehovKode(): String? {
                return null
            }
        }

        class Journalpostinfo(val journalpostId: Long) : Journalpostreferanse {
            override fun hentJournalpostreferanse(): Long {
                return journalpostId
            }

            override fun hentAvklaringsbehovKode(): String? {
                return null
            }
        }

        class IngenReferanse(val noe: String)

        fun NormalOpenAPIRoute.getTestRoute() {
            route("testApi/sak/{saksnummer}") {
                authorizedGet<TestReferanse, Saksinfo>(
                    SakPathParam("saksnummer")
                ) { req ->
                    respond(Saksinfo(saksnummer = req.saksnummer))
                }
            }
        }


        val saksnummerTilJournalpost = mutableMapOf<UUID, Long>()

        fun NormalOpenAPIRoute.getJournalpostTestRoute() {
            route("testApi/journalpost/{saksnummer}") {
                authorizedGet<TestReferanse, Saksinfo>(
                    { params, _ -> requireNotNull(saksnummerTilJournalpost[params.saksnummer]) }
                ) { req ->
                    respond(Saksinfo(saksnummer = req.saksnummer))
                }
            }
        }

        fun NormalOpenAPIRoute.postTestRouteSak() {
            route(
                "testApi/sak",
            ) {
                authorizedSakPost<Unit, Saksinfo, Saksinfo>(
                    Operasjon.SAKSBEHANDLE,
                ) { _, dto ->
                    respond(Saksinfo(saksnummer = uuid))
                }
            }
        }

        fun NormalOpenAPIRoute.putTestRouteSak() {
            route(
                "testApi/sak/put",
            ) {
                authorizedPut<Unit, Saksinfo, Saksinfo>(
                    AuthorizationBodyPathConfig(Operasjon.SAKSBEHANDLE)
                ) { _, dto ->
                    respond(Saksinfo(saksnummer = uuid))
                }
            }
        }

        fun NormalOpenAPIRoute.postTestRouteBehandling() {
            route(
                "testApi/behandling",
            ) {
                authorizedBehandlingPost<Unit, Behandlingsinfo, Behandlingsinfo>(
                    Operasjon.SAKSBEHANDLE,
                ) { _, dto ->
                    respond(Behandlingsinfo(behandlingsnummer = uuid))
                }
            }
        }

        fun NormalOpenAPIRoute.postTestRouteJournalpost() {
            route(
                "testApi/journalpost",
            ) {
                authorizedPost<Unit, Journalpostinfo, Journalpostinfo>(
                    { params, body -> requireNotNull(body.journalpostId) },
                    { request: Journalpostinfo -> requireNotNull(request); request.journalpostId.toString() },
                    Operasjon.SAKSBEHANDLE,
                ) { _, dto ->
                    respond(Journalpostinfo(dto.journalpostId))
                }
            }
        }

        private val server =
            embeddedServer(Netty, port = 8080) {
                commonKtorModule(
                    PrometheusMeterRegistry(PrometheusConfig.DEFAULT),
                    AzureConfig(),
                    InfoModel()
                )
                routing {
                    authenticate(AZURE) {
                        apiRouting {
                            getTestRoute()
                            getJournalpostTestRoute()
                            postTestRouteSak()
                            putTestRouteSak()
                            postTestRouteBehandling()
                            postTestRouteJournalpost()
                        }
                    }
                }
                module(fakes)
            }

        val autorisertEksempelAppServer = embeddedServer(Netty, port = 8082) { autorisertEksempelApp() }

        val uuid = UUID.randomUUID()

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            fakes.gittTilgangTilSak(uuid.toString(), true)
            fakes.gittTilgangTilBehandling(uuid.toString(), true)
            server.start()
            autorisertEksempelAppServer.start()
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            fakes.close()
            server.stop()
            autorisertEksempelAppServer.stop()
        }

        private fun Application.module(fakes: Fakes) {
            // Setter opp virtuell sandkasse lokalt
            monitor.subscribe(ApplicationStopped) { application ->
                application.environment.log.info("Server har stoppet")
                fakes.close()
                // Release resources and unsubscribe from events
                application.monitor.unsubscribe(ApplicationStopped) {}
            }
        }

        data class TestReferanse(
            @PathParam(description = "saksnummer") val saksnummer: UUID = UUID.randomUUID()
        )

    }

    @Test
    fun `get route som støtter on-behalf-of gir tilgang med on-behalf-of token`() {
        val randomUuid = UUID.randomUUID()
        fakes.gittTilgangTilSak(randomUuid.toString(), true)
        val res = clientForOBO.get<Saksinfo>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedGet/$randomUuid/on-behalf-of"),
            GetRequest(currentToken = getOboToken())
        )

        assertThat(res?.saksnummer).isEqualTo(randomUuid)
    }

    @Test
    fun `get route som støtter client-credentials gir tilgang med client-credentials token uten at tilgang-tjenesten kalles`() {
        val randomUuid = UUID.randomUUID()
        val res = clientForClientCredentials.get<Saksinfo>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedGet/$randomUuid/client-credentials"),
            GetRequest()
        )

        assertThat(res?.saksnummer).isEqualTo(randomUuid)
    }

    @Test
    fun `get route som støtter on-behalf-of og client-credentials gir tilgang med on-behalf-of token og client-credentials token`() {
        val randomUuid = UUID.randomUUID()
        fakes.gittTilgangTilSak(randomUuid.toString(), true)
        val res1 = clientForOBO.get<Saksinfo>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedGet/$randomUuid/client-credentials-and-on-behalf-of"),
            GetRequest(currentToken = getOboToken())
        )
        val res2 = clientForClientCredentials.get<Saksinfo>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedGet/$randomUuid/client-credentials-and-on-behalf-of"),
            GetRequest()
        )

        assertThat(res1?.saksnummer).isEqualTo(randomUuid)
        assertThat(res2?.saksnummer).isEqualTo(randomUuid)
    }

    @Test
    fun `post route som støtter on-behalf-of gir tilgang med on-behalf-of token`() {
        val randomUuid = UUID.randomUUID()
        fakes.gittTilgangTilSak(randomUuid.toString(), true)
        val res = clientForOBO.post<_, Saksinfo>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedPost/on-behalf-of"),
            PostRequest(Saksinfo(randomUuid), currentToken = getOboToken())
        )

        assertThat(res?.saksnummer).isEqualTo(uuid)
    }

    @Test
    fun `post route som støtter client-credentials gir tilgang med client-credentials token uten å ha en tilgang-referanse`() {
        val randomUuid = UUID.randomUUID()
        val res = clientForClientCredentials.post<_, IngenReferanse>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedPost/client-credentials"),
            PostRequest(IngenReferanse(randomUuid.toString()), currentToken = getOboToken())
        )

        assertThat(res?.noe).isEqualTo(randomUuid.toString())
    }

    @Test
    fun `post som støtter on-behalf-of token og client-credentials gir tilgang med on-behalf-of token og client-credentials token`() {
        val randomUuid = UUID.randomUUID()
        fakes.gittTilgangTilSak(randomUuid.toString(), true)
        val res1 = clientForOBO.post<_, Saksinfo>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedPost/client-credentials-and-on-behalf-of"),
            PostRequest(Saksinfo(randomUuid), currentToken = getOboToken())
        )
        val res2 = clientForClientCredentials.post<_, Saksinfo>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedPost/client-credentials-and-on-behalf-of"),
            PostRequest(Saksinfo(randomUuid), currentToken = getOboToken())
        )

        assertThat(res1?.saksnummer).isEqualTo(uuid)
        assertThat(res2?.saksnummer).isEqualTo(uuid)
    }

    @Test
    fun `Skal kunne hente saksnummer fra path params`() {
        val randomUuid = UUID.randomUUID()
        fakes.gittTilgangTilSak(randomUuid.toString(), true)
        val res = clientForOBO.get<Saksinfo>(
            URI.create("http://localhost:8080/")
                .resolve("testApi/sak/$randomUuid"),
            GetRequest(currentToken = getOboToken())
        )

        assertThat(res?.saksnummer).isEqualTo(randomUuid)
    }

    @Test
    fun `Skal kunne hente journalpost fra path params`() {
        val saksnummer = UUID.randomUUID()
        val journalpostId = Random.nextLong()
        fakes.gittTilgangTilJournalpost(journalpostId, true)
        saksnummerTilJournalpost[saksnummer] = journalpostId
        val res = clientForOBO.get<Saksinfo>(
            URI.create("http://localhost:8080/")
                .resolve("testApi/journalpost/$saksnummer"),
            GetRequest(currentToken = getOboToken())
        )

        assertThat(res?.saksnummer).isEqualTo(saksnummer)
    }

    @Test
    fun `Skal kunne hente saksnummer fra request body`() {
        val res = clientForOBO.post<_, Saksinfo>(
            URI.create("http://localhost:8080/")
                .resolve("testApi/sak"),
            PostRequest(Saksinfo(uuid), currentToken = getOboToken())
        )

        assertThat(res?.saksnummer).isEqualTo(uuid)
    }

    @Test
    fun `Skal kunne hente saksnummer fra request body med put`() {
        val res = clientForOBO.put<_, Saksinfo>(
            URI.create("http://localhost:8080/")
                .resolve("testApi/sak/put"),
            PutRequest(Saksinfo(uuid), currentToken = getOboToken())
        )

        assertThat(res?.saksnummer).isEqualTo(uuid)
    }

    @Test
    fun `Skal kunne hente behandlingsnummer fra request body`() {
        val res = clientForOBO.post<_, Behandlingsinfo>(
            URI.create("http://localhost:8080/")
                .resolve("testApi/behandling"),
            PostRequest(Behandlingsinfo(uuid), currentToken = getOboToken())
        )

        assertThat(res?.behandlingsnummer).isEqualTo(uuid)
    }

    @Test
    fun `Skal kunne hente journalpostid fra request body`() {
        val journalpostId = 1337L
        fakes.gittTilgangTilJournalpost(journalpostId, true)
        val res = clientForOBO.post<_, Journalpostinfo>(
            URI.create("http://localhost:8080/")
                .resolve("testApi/journalpost"),
            PostRequest(Journalpostinfo(journalpostId), currentToken = getOboToken())
        )

        assertThat(res?.journalpostId).isEqualTo(journalpostId)
    }

}
