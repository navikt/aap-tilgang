import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.error.DefaultResponseHandler
import no.nav.aap.komponenter.httpklient.httpclient.error.ManglerTilgangException
import no.nav.aap.komponenter.httpklient.httpclient.get
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.GetRequest
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.NoTokenTokenProvider
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.OnBehalfOfTokenProvider
import no.nav.aap.tilgang.*
import no.nav.aap.tilgang.plugin.kontrakt.Journalpostreferanse
import no.nav.aap.tilgang.plugin.kontrakt.Saksreferanse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI
import java.util.*

class TilgangPluginTest {
    companion object {
        private val azureTokenGen = AzureTokenGen("behandlingsflyt", "behandlingsflyt")
        private val fakes = Fakes(azurePort = 8081, azureTokenGen)

        private val clientForClientCredentials = RestClient(
            config = ClientConfig(scope = "behandlingsflyt"),
            tokenProvider = ClientCredentialsTokenProvider,
            responseHandler = DefaultResponseHandler()
        )
        private val clientForOBO = RestClient.withDefaultResponseHandler(
            config = ClientConfig(scope = "behandlingsflyt"),
            tokenProvider = OnBehalfOfTokenProvider,
        )
        private val clientUtenTokenProvider = RestClient(
            config = ClientConfig(scope = "behandlingsflyt"),
            tokenProvider = NoTokenTokenProvider(),
            responseHandler = DefaultResponseHandler()
        )

        private fun generateToken(isApp: Boolean, roles: List<String> = emptyList()): OidcToken {
            return OidcToken(azureTokenGen.generate(isApp, roles))
        }

        class Saksinfo(val saksnummer: UUID) : Saksreferanse {
            override fun hentSaksreferanse(): String {
                return saksnummer.toString()
            }
        }

        data class Journalpostinfo(@PathParam("behandlingReferanse") val behandlingReferanse: String) :
            Journalpostreferanse {
            override fun journalpostIdResolverInput(): String {
                return behandlingReferanse
            }

            override fun hentAvklaringsbehovKode(): String? {
                return null
            }
        }

        class IngenReferanse(val noe: String)

        val autorisertEksempelAppServer = embeddedServer(Netty, port = 8082) { autorisertEksempelApp() }

        val uuid = UUID.randomUUID()

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            fakes.gittTilgangTilSak(uuid.toString(), true)
            fakes.gittTilgangTilBehandling(uuid.toString(), true)
            autorisertEksempelAppServer.start()
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            fakes.close()
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
            GetRequest(currentToken = generateToken(isApp = false))
        )

        assertThat(res?.saksnummer).isEqualTo(randomUuid)
    }

    @Test
    fun `get route som støtter client-credentials med approvedApplications gir tilgang med client-credentials token uten at tilgang-tjenesten kalles`() {
        val randomUuid = UUID.randomUUID()
        val res = clientForClientCredentials.get<Saksinfo>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedGet/$randomUuid/client-credentials-approved-applications"),
            GetRequest()
        )

        assertThat(res?.saksnummer).isEqualTo(randomUuid)
    }

    @Test
    fun `get route som støtter client-credentials med rolle gir tilgang med riktig rolle`() {
        val randomUuid = UUID.randomUUID()
        val token = generateToken(isApp = true, roles = listOf("tilgang-rolle"))
        val res = clientUtenTokenProvider.get<Saksinfo>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedGet/$randomUuid/client-credentials-application-role"),
            GetRequest(additionalHeaders = listOf(Header("Authorization", "Bearer ${token.token()}")))
        )

        assertThat(res?.saksnummer).isEqualTo(randomUuid)
    }

    @Test
    fun `get route som støtter client-credentials med rolle gir ikke tilgang med feil rolle`() {
        val randomUuid = UUID.randomUUID()
        val token = generateToken(isApp = true, roles = listOf("feil-rolle"))
        assertThrows<ManglerTilgangException> {
            clientUtenTokenProvider.get<Saksinfo>(
                URI.create("http://localhost:8082/")
                    .resolve("testApi/authorizedGet/$randomUuid/client-credentials-application-role"),
                GetRequest(additionalHeaders = listOf(Header("Authorization", "Bearer ${token.token()}")))
            )
        }
    }


    @Test
    fun `get route som støtter on-behalf-of og client-credentials gir tilgang med on-behalf-of token og client-credentials token`() {
        val randomUuid = UUID.randomUUID()
        fakes.gittTilgangTilSak(randomUuid.toString(), true)
        val res1 = clientForOBO.get<Saksinfo>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedGet/$randomUuid/client-credentials-and-on-behalf-of"),
            GetRequest(currentToken = generateToken(isApp = false))
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
            PostRequest(Saksinfo(randomUuid), currentToken = generateToken(isApp = false))
        )

        assertThat(res?.saksnummer).isEqualTo(uuid)
    }

    @Test
    fun `post route som støtter client-credentials gir tilgang med client-credentials token uten å ha en tilgang-referanse`() {
        val randomUuid = UUID.randomUUID()
        val res = clientForClientCredentials.post<_, IngenReferanse>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedPost/client-credentials-approved-applications"),
            PostRequest(IngenReferanse(randomUuid.toString()))
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
            PostRequest(Saksinfo(randomUuid), currentToken = generateToken(isApp = false))
        )
        val res2 = clientForClientCredentials.post<_, Saksinfo>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedPost/client-credentials-and-on-behalf-of"),
            PostRequest(Saksinfo(randomUuid))
        )

        assertThat(res1?.saksnummer).isEqualTo(uuid)
        assertThat(res2?.saksnummer).isEqualTo(uuid)
    }

    @Test
    fun `post som støtter client-credentials med rolle gir tilgang med riktig rolle`() {
        val randomUuid = UUID.randomUUID()
        val token = generateToken(isApp = true, roles = listOf("tilgang-rolle"))
        val res = clientUtenTokenProvider.post<_, IngenReferanse>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedPost/client-credentials-application-role"),
            PostRequest(
                IngenReferanse(randomUuid.toString()),
                additionalHeaders = listOf(Header("Authorization", "Bearer ${token.token()}"))
            )
        )

        assertThat(res?.noe).isEqualTo(randomUuid.toString())
    }

    @Test
    fun `post som støtter client-credentials med rolle gir ikke tilgang med feil rolle`() {
        val randomUuid = UUID.randomUUID()
        val token = generateToken(isApp = true, roles = listOf("feil-rolle"))
        assertThrows<ManglerTilgangException> {
            clientUtenTokenProvider.post<_, IngenReferanse>(
                URI.create("http://localhost:8082/")
                    .resolve("testApi/authorizedPost/client-credentials-application-role"),
                PostRequest(
                    IngenReferanse(randomUuid.toString()),
                    additionalHeaders = listOf(Header("Authorization", "Bearer ${token.token()}"))
                )
            )
        }
    }


    @Test
    fun `post støtter path params`() {
        val behandlingsRef = "123"
        val journalpostId = 456L
        fakes.gittTilgangTilJournalpost(journalpostId, true)
        val res = clientForOBO.post<_, IngenReferanse>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/pathForPost/$behandlingsRef"),
            PostRequest(IngenReferanse("test"), currentToken = generateToken(isApp = false))
        )

        assertThat(res?.noe).isEqualTo("test")
    }
}
