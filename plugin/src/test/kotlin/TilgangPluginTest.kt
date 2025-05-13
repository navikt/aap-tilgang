import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
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

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            System.setProperty("AAP_BESLUTTER", "en-eller-annen-uid")
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

        data class PersonIdentReferanse(
            @PathParam(description = "personIdent") val personIdent: String
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
    fun `Skal gi tilgang kun basert på rolle`() {
        val token = generateToken(isApp = false, roles = listOf(Beslutter.id))

        val res = clientUtenTokenProvider.get<IngenReferanse>(
            URI.create("http://localhost:8082/")
                .resolve("kun-roller"),
            GetRequest(additionalHeaders = listOf(Header("Authorization", "Bearer ${token.token()}")))
        )

        assertThat(res?.noe).isEqualTo("test")
    }

    @Test
    fun `Skal ikke gi tilgang for manglende rolle`() {
        val token = generateToken(isApp = false, roles = listOf("ikke-riktig-rolle"))
        assertThrows<ManglerTilgangException> {
            clientUtenTokenProvider.get<IngenReferanse>(
                URI.create("http://localhost:8082/")
                    .resolve("kun-roller"),
                GetRequest(additionalHeaders = listOf(Header("Authorization", "Bearer ${token.token()}")))
            )
        }
    }

    @Test
    fun `get route som støtter on-behalf-of gir ikke tilgang med client credentials token`() {
        val randomUuid = UUID.randomUUID()
        fakes.gittTilgangTilSak(randomUuid.toString(), true)
        assertThrows<ManglerTilgangException> {
            clientForClientCredentials.get<Saksinfo>(
                URI.create("http://localhost:8082/")
                    .resolve("testApi/authorizedGet/$randomUuid/on-behalf-of"),
                GetRequest(currentToken = generateToken(isApp = false))
            )
        }
    }

    @Test
    fun `get route som støtter client-credentials med rolle gir tilgang med riktig rolle uten at tilgang-tjenesten kalles`() {
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
    fun `get route kan sjekke tilgang til person`() {
        val person = "12345"
        fakes.gittTilgangTilPerson(person, true)
        val res = clientForOBO.get<Long>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/person/get/$person"),
            GetRequest(currentToken = generateToken(isApp = false))
        )
        assertThat(res).isEqualTo(123)

        val person2 = "123456"
        assertThrows<ManglerTilgangException> {
            clientForOBO.get<Long>(
                URI.create("http://localhost:8082/")
                    .resolve("testApi/person/get/$person2"),
                GetRequest(currentToken = generateToken(isApp = false))
            )
        }
    }

    @Test
    fun `post route kan sjekke tilgang til person`() {
        val person = "234"
        fakes.gittTilgangTilPerson(person, true)
        val res = clientForOBO.post<_, Personinfo>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/person/post"),
            PostRequest(Personinfo(person), currentToken = generateToken(isApp = false))
        )
        assertThat(res?.personIdent).isEqualTo("234")

        val person2 = "123456"
        assertThrows<ManglerTilgangException> {
            clientForOBO.post<_, Personinfo>(
                URI.create("http://localhost:8082/")
                    .resolve("testApi/person/post"),
                PostRequest(Personinfo(person2), currentToken = generateToken(isApp = false))
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

        val token = generateToken(isApp = true, roles = listOf("tilgang-rolle"))
        val res2 = clientUtenTokenProvider.get<Saksinfo>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedGet/$randomUuid/client-credentials-and-on-behalf-of"),
            GetRequest(additionalHeaders = listOf(Header("Authorization", "Bearer ${token.token()}")))
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
                .resolve("testApi/authorizedPost/on-behalf-of/saksinfo"),
            PostRequest(Saksinfo(randomUuid), currentToken = generateToken(isApp = false))
        )

        assertThat(res?.saksnummer).isEqualTo(randomUuid)
    }

    @Test
    fun `post route som slår opp behandlingreferanse basert på annen referanse`() {
        val enAnnenReferanse = UUID.randomUUID()
        val behandlingReferanse = UUID.randomUUID()
        fakes.gittTilgangTilBehandling(behandlingReferanse, true)
        enAnnenReferanseTilbehandlingReferanse.put(enAnnenReferanse.toString(), behandlingReferanse)
        val res = clientForOBO.post<_, Behandlinginfo>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedPost/on-behalf-of/behandlinginfo"),
            PostRequest(Behandlinginfo(enAnnenReferanse.toString()), currentToken = generateToken(isApp = false))
        )

        assertThat(res?.enAnnenReferanse).isEqualTo(enAnnenReferanse.toString())
    }

    @Test
    fun `post route som støtter client-credentials gir tilgang med client-credentials token uten å ha en tilgang-referanse`() {
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
    fun `post som støtter on-behalf-of token og client-credentials gir tilgang med on-behalf-of token og client-credentials token`() {
        val saksnummer = UUID.randomUUID()
        fakes.gittTilgangTilSak(saksnummer.toString(), true)
        val res1 = clientForOBO.post<_, Saksinfo>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedPost/client-credentials-and-on-behalf-of"),
            PostRequest(Saksinfo(saksnummer), currentToken = generateToken(isApp = false))
        )
        val token = generateToken(isApp = true, roles = listOf("tilgang-rolle"))
        val res2 = clientUtenTokenProvider.post<_, Saksinfo>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedPost/client-credentials-and-on-behalf-of"),
            PostRequest(
                Saksinfo(saksnummer),
                additionalHeaders = listOf(Header("Authorization", "Bearer ${token.token()}"))
            )
        )

        assertThat(res1?.saksnummer).isEqualTo(saksnummer)
        assertThat(res2?.saksnummer).isEqualTo(saksnummer)
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
    fun `post støtter path params med mapping fra annen referanse til journalpostId`() {
        val behandlingsRef = "123"
        val journalpostId = 456L
        fakes.gittTilgangTilJournalpost(journalpostId, true)
        val res = clientForOBO.post<_, IngenReferanse>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/pathForPost/resolve/journalpost/$behandlingsRef"),
            PostRequest(IngenReferanse("test"), currentToken = generateToken(isApp = false))
        )

        assertThat(res?.noe).isEqualTo("test")
    }

    @Test
    fun `post støtter path params med mapping fra annen referanse til behandlingreferanse`() {
        val enAnnenReferanse = UUID.randomUUID()
        val behandlingReferanse = UUID.randomUUID()
        fakes.gittTilgangTilBehandling(behandlingReferanse, true)
        enAnnenReferanseTilbehandlingReferanse.put(enAnnenReferanse.toString(), behandlingReferanse)
        val res = clientForOBO.post<_, IngenReferanse>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/pathForPost/resolve/behandlingreferanse/$enAnnenReferanse"),
            PostRequest(IngenReferanse("test"), currentToken = generateToken(isApp = false))
        )

        assertThat(res?.noe).isEqualTo("test")
    }

    @Test
    fun `skal auditlogge - path resolver`() {
        val randomUuid = UUID.randomUUID()
        fakes.gittTilgangTilSak(randomUuid.toString(), true)

        val logger = LoggerFactory.getLogger(TILGANG_PLUGIN) as Logger
        val appender = LogCaptureAppender()
        appender.start()
        logger.addAppender(appender)


        val res = clientForOBO.get<Saksinfo>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedGet/$randomUuid/on-behalf-of"),
            GetRequest(currentToken = generateToken(isApp = false))
        )

        assertThat(res?.saksnummer).isEqualTo(randomUuid)

        val messages = appender.getLoggedMessages()
        val expected =
            messages.first { it.contains("CEF:0|Kelvin|behandlingsflyt|1.0|audit:access|Auditlogg|INFO|flexString1=Permit request=/testApi/authorizedGet/$randomUuid/on-behalf-of duid=12345678901 flexString1Label=Decision end=") }
        assertNotNull(expected)
        assertTrue(expected.contains("suid=Lokalsaksbehandler"))

        // Clean up
        logger.detachAppender(appender)

    }

    @Test
    fun `skal auditlogge - body resolver`() {
        val randomUuid = UUID.randomUUID()
        fakes.gittTilgangTilSak(randomUuid.toString(), true)

        val logger = LoggerFactory.getLogger(TILGANG_PLUGIN) as Logger
        val appender = LogCaptureAppender()
        appender.start()
        logger.addAppender(appender)


        val res = clientForOBO.post<_, RequestMedAuditResolver>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedPost/med-audit-resolver"),
            PostRequest(
                RequestMedAuditResolver(saksreferanse = randomUuid),
                currentToken = generateToken(isApp = false)
            )
        )

        assertThat(res?.saksreferanse).isEqualTo(randomUuid)

        val messages = appender.getLoggedMessages()
        val expected =
            messages.first { it.contains("CEF:0|Kelvin|behandlingsflyt|1.0|audit:access|Auditlogg|INFO|flexString1=Permit request=/testApi/authorizedPost/med-audit-resolver duid=12345678901 flexString1Label=Decision end=") }
        assertNotNull(expected)
        assertTrue(expected.contains("suid=Lokalsaksbehandler"))

        // Clean up
        logger.detachAppender(appender)
    }

    @Test
    fun `Maskin-til-maskin`() {
        val randomUuid = UUID.randomUUID()
        val token = generateToken(isApp = true, roles = listOf("tilgang-rolle"))
        val res = clientUtenTokenProvider.get<Saksinfo>(
            URI.create("http://localhost:8082/")
                .resolve("testApi/authorizedGet/$randomUuid/application-role-machine-to-machine"),
            GetRequest(
                additionalHeaders = listOf(Header("Authorization", "Bearer ${token.token()}"))
            )
        )

        assertThat(res?.saksnummer).isEqualTo(randomUuid)
    }

    @Test
    fun `get route maskin-til-maskin gir ikke tilgang med feil rolle`() {
        val randomUuid = UUID.randomUUID()
        val token = generateToken(isApp = true, roles = listOf("feil-rolle"))
        assertThrows<ManglerTilgangException> {
            clientUtenTokenProvider.get<Saksinfo>(
                URI.create("http://localhost:8082/")
                    .resolve("testApi/authorizedGet/$randomUuid/application-role-machine-to-machine"),
                GetRequest(additionalHeaders = listOf(Header("Authorization", "Bearer ${token.token()}")))
            )
        }
    }

    @Test
    fun `skal returnere json ved ikke tilgang`() {
        val randomUuid = UUID.randomUUID()
        assertThatThrownBy {
            clientForOBO.get<Saksinfo>(
                URI.create("http://localhost:8082/")
                    .resolve("testApi/authorizedGet/$randomUuid/on-behalf-of"),
                GetRequest(currentToken = generateToken(isApp = false))
            )
        }.isInstanceOf(ManglerTilgangException::class.java).extracting("body").isEqualTo("{\"message\":\"Ingen tilgang\",\"code\":\"UKJENT_FEIL\"}")
    }
}

class LogCaptureAppender : AppenderBase<ILoggingEvent>() {
    private val events = mutableListOf<ILoggingEvent>()

    override fun append(eventObject: ILoggingEvent) {
        events.add(eventObject)
    }

    fun getLoggedMessages(): List<String> = events.map { it.formattedMessage }
}

