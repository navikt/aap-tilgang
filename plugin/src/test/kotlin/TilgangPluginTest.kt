import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import com.papsign.ktor.openapigen.route.apiRouting
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.DefaultResponseHandler
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.put
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.request.PutRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.tilgang.*
import no.nav.aap.tilgang.plugin.kontrakt.Behandlingsreferanse
import no.nav.aap.tilgang.plugin.kontrakt.Journalpostreferanse
import no.nav.aap.tilgang.plugin.kontrakt.Saksreferanse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import tilgang.Operasjon
import java.net.URI
import java.util.*

class TilgangPluginTest {
    companion object {
        private val fakes = Fakes(azurePort = 8081)

        private val client = RestClient(
            config = ClientConfig(scope = "behandlingsflyt"),
            tokenProvider = ClientCredentialsTokenProvider,
            responseHandler = DefaultResponseHandler()
        )

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

        fun NormalOpenAPIRoute.getTestRoute() {
            route("testApi/sak/{saksnummer}") {
                authorizedGet<TestReferanse, Saksinfo>(
                    SakPathParam("saksnummer")
                ) { req ->
                    respond(Saksinfo(saksnummer = req.saksnummer))
                }
            }
        }

        fun NormalOpenAPIRoute.getTestRoute2() {
            route("testApi/sak2/{saksnummer}") {
                authorizedGet<TestReferanse, Saksinfo>(
                    AuthorizationParamPathConfig(sakPathParam = SakPathParam("saksnummer"))
                ) { req ->
                    respond(Saksinfo(saksnummer = req.saksnummer))
                }
            }
        }

        fun NormalOpenAPIRoute.getJournalpostTestRoute() {
            route("testApi/journalpost/{saksnummer}") {
                authorizedGet<TestReferanse, Saksinfo>(
                    { params, _ -> requireNotNull(params.saksnummer); 1L }
                ) { req ->
                    respond(Saksinfo(saksnummer = req.saksnummer))
                }
            }
        }

        val uuid = UUID.randomUUID()
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

        fun NormalOpenAPIRoute.postTestRouteSak2() {
            route(
                "testApi/sak2",
            ) {
                authorizedPost<Unit, Saksinfo, Saksinfo>(
                    AuthorizationBodyPathConfig(Operasjon.SAKSBEHANDLE)
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
                    { params, body -> requireNotNull(body.journalpostId); 1L },
                    { request: Journalpostinfo -> requireNotNull(request); request.journalpostId.toString() },
                    Operasjon.SAKSBEHANDLE,
                ) { _, dto ->
                    respond(Journalpostinfo(1337L))
                }
            }
        }

        // Starter server
        private val server = embeddedServer(Netty, port = 8080) {
            install(OpenAPIGen)
            install(ContentNegotiation) {
                jackson()
            }
            apiRouting {
                getTestRoute()
                getTestRoute2()
                getJournalpostTestRoute()
                postTestRouteSak()
                postTestRouteSak2()
                putTestRouteSak()
                postTestRouteBehandling()
                postTestRouteJournalpost()
            }
            module(fakes)
        }.start()

        @JvmStatic
        @AfterAll
        fun afterAll() {
            fakes.close()
            server.stop()
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
    fun `Skal kunne hente saksnummer fra path params`() {
        val randomUuid = UUID.randomUUID()
        val res = client.get<Saksinfo>(
            URI.create("http://localhost:8080/")
                .resolve("testApi/sak/$randomUuid"),
            GetRequest()
        )

        assertThat(res?.saksnummer).isEqualTo(randomUuid)
    }

    @Test
    fun `Skal kunne hente saksnummer fra path params2`() {
        val randomUuid = UUID.randomUUID()
        val res = client.get<Saksinfo>(
            URI.create("http://localhost:8080/")
                .resolve("testApi/sak2/$randomUuid"),
            GetRequest()
        )

        assertThat(res?.saksnummer).isEqualTo(randomUuid)
    }

    @Test
    fun `Skal kunne hente journalpost fra path params`() {
        val randomUuid = UUID.randomUUID()
        val res = client.get<Saksinfo>(
            URI.create("http://localhost:8080/")
                .resolve("testApi/journalpost/$randomUuid"),
            GetRequest()
        )

        assertThat(res?.saksnummer).isEqualTo(randomUuid)
    }

    @Test
    fun `Skal kunne hente saksnummer fra request body`() {
        val res = client.post<_, Saksinfo>(
            URI.create("http://localhost:8080/")
                .resolve("testApi/sak"),
            PostRequest(Saksinfo(uuid))
        )

        assertThat(res?.saksnummer).isEqualTo(uuid)
    }

    @Test
    fun `Skal kunne hente saksnummer 2 fra request body`() {
        val res = client.post<_, Saksinfo>(
            URI.create("http://localhost:8080/")
                .resolve("testApi/sak2"),
            PostRequest(Saksinfo(uuid))
        )

        assertThat(res?.saksnummer).isEqualTo(uuid)
    }

    @Test
    fun `Skal kunne hente saksnummer fra request body med put`() {
        val res = client.put<_, Saksinfo>(
            URI.create("http://localhost:8080/")
                .resolve("testApi/sak/put"),
            PutRequest(Saksinfo(uuid))
        )

        assertThat(res?.saksnummer).isEqualTo(uuid)
    }

    @Test
    fun `Skal kunne hente behandlingsnummer fra request body`() {
        val res = client.post<_, Behandlingsinfo>(
            URI.create("http://localhost:8080/")
                .resolve("testApi/behandling"),
            PostRequest(Behandlingsinfo(uuid))
        )

        assertThat(res?.behandlingsnummer).isEqualTo(uuid)
    }

    @Test
    fun `Skal kunne hente journalpostid fra request body`() {
        val res = client.post<_, Journalpostinfo>(
            URI.create("http://localhost:8080/")
                .resolve("testApi/journalpost"),
            PostRequest(Journalpostinfo(1337L))
        )

        assertThat(res?.journalpostId).isEqualTo(1337L)
    }

}
