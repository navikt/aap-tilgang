import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.papsign.ktor.openapigen.annotations.parameters.PathParam
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.jackson.jackson
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.runBlocking
import no.nav.aap.komponenter.httpklient.httpclient.error.ManglerTilgangException
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.tilgang.Beslutter
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.Rolle
import no.nav.aap.tilgang.TILGANG_PLUGIN
import no.nav.aap.tilgang.plugin.kontrakt.Journalpostreferanse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.LoggerFactory
import kotlin.booleanArrayOf

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TilgangPluginTest {
    companion object {
        private val azureTokenGen = AzureTokenGen("behandlingsflyt", "behandlingsflyt")
        private val fakes = Fakes(azureTokenGen)

        private fun generateToken(isApp: Boolean, roles: List<String> = emptyList(), azp: UUID? = null): OidcToken {
            return OidcToken(azureTokenGen.generate(isApp, roles, azp))
        }

        data class Journalpostinfo(@param:PathParam("behandlingReferanse") val behandlingReferanse: String) :
            Journalpostreferanse {
            override fun journalpostIdResolverInput(): String {
                return behandlingReferanse
            }

            override fun hentPåkrevdRolle(): List<Rolle> {
                return emptyList()
            }
        }

        class IngenReferanse(val noe: String)

        val autorisertEksempelAppServer =
            embeddedServer(Netty, port = 8082) { autorisertEksempelApp() }

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

        data class TestReferanse(
            @param:PathParam(description = "saksnummer") val saksnummer: UUID = UUID.randomUUID()
        )
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) { jackson() }
        expectSuccess = false
    }

    private val texasExchangeUrl: String = System.getProperty("NAIS_TOKEN_EXCHANGE_ENDPOINT")
    private val texasTokenUrl: String = System.getProperty("NAIS_TOKEN_ENDPOINT")

    private suspend fun oboAccessToken(currentToken: OidcToken): String =
        httpClient.post(texasExchangeUrl) {
            contentType(ContentType.Application.Json)
            setBody(
                mapOf(
                    "identity_provider" to "entra_id",
                    "target" to "behandlingsflyt",
                    "user_token" to currentToken.token()
                )
            )
        }.body<Map<String, String>>()["access_token"]!!

    private suspend fun m2mAccessToken(): String =
        httpClient.post(texasTokenUrl) {
            contentType(ContentType.Application.Json)
            setBody(mapOf("identity_provider" to "entra_id", "target" to "behandlingsflyt"))
        }.body<Map<String, String>>()["access_token"]!!

    private suspend fun HttpResponse.orThrowIfForbidden(): HttpResponse {
        if (status == HttpStatusCode.Forbidden) throw ManglerTilgangException("Mangler tilgang", bodyAsText())
        return this
    }

    private suspend inline fun <reified T> oboGet(url: String, currentToken: OidcToken): T? {
        val resp = httpClient.get(url) { bearerAuth(oboAccessToken(currentToken)) }.orThrowIfForbidden()
        return if (resp.status.isSuccess()) resp.body() else null
    }

    private suspend inline fun <reified T, reified B : Any> oboPost(url: String, body: B, currentToken: OidcToken): T? {
        val resp = httpClient.post(url) {
            bearerAuth(oboAccessToken(currentToken))
            contentType(ContentType.Application.Json)
            setBody(body)
        }.orThrowIfForbidden()
        return if (resp.status.isSuccess()) resp.body() else null
    }

    private suspend inline fun <reified T> m2mGet(url: String): T? {
        val resp = httpClient.get(url) { bearerAuth(m2mAccessToken()) }.orThrowIfForbidden()
        return if (resp.status.isSuccess()) resp.body() else null
    }

    private suspend inline fun <reified T> bearerGet(url: String, token: String): T? {
        val resp = httpClient.get(url) { bearerAuth(token) }.orThrowIfForbidden()
        return if (resp.status.isSuccess()) resp.body() else null
    }

    private suspend inline fun <reified T, reified B : Any> bearerPost(url: String, body: B, token: String): T? {
        val resp = httpClient.post(url) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.orThrowIfForbidden()
        return if (resp.status.isSuccess()) resp.body() else null
    }

    private fun base(path: String) = URI.create("http://localhost:8082/").resolve(path).toString()

    @Test
    fun `get route som støtter on-behalf-of gir tilgang med on-behalf-of token`() {
        val randomUuid = UUID.randomUUID()
        fakes.gittTilgangTilSak(randomUuid.toString(), true)
        runBlocking {
            val res =
                oboGet<Saksinfo>(base("testApi/authorizedGet/$randomUuid/on-behalf-of"), generateToken(isApp = false))
            assertThat(res?.saksnummer).isEqualTo(randomUuid)
        }
    }

    @Test
    fun `Skal gi tilgang kun basert på rolle`() {
        val token = generateToken(isApp = false, roles = listOf(Beslutter.id))
        runBlocking {
            val res = bearerGet<IngenReferanse>(base("kun-roller"), token.token())
            assertThat(res?.noe).isEqualTo("test")
        }
    }

    @Test
    fun `Skal gi tilgang basert på rolle for POST på samme route som GET`() {
        val token = generateToken(isApp = false, roles = listOf(Beslutter.id))
        runBlocking {
            val res =
                bearerPost<IngenReferanse, IngenReferanse>(base("kun-roller"), IngenReferanse("input"), token.token())
            assertThat(res?.noe).isEqualTo("post-input")
        }
    }

    @Test
    fun `Skal ikke gi tilgang for manglende rolle på POST på samme route som GET`() {
        val token = generateToken(isApp = false, roles = listOf("ikke-riktig-rolle"))
        assertThrows<ManglerTilgangException> {
            runBlocking {
                bearerPost<IngenReferanse, IngenReferanse>(base("kun-roller"), IngenReferanse("input"), token.token())
            }
        }
    }

    @Test
    fun `Skal ikke gi tilgang for manglende rolle`() {
        val token = generateToken(isApp = false, roles = listOf("ikke-riktig-rolle"))
        assertThrows<ManglerTilgangException> {
            runBlocking { bearerGet<IngenReferanse>(base("kun-roller"), token.token()) }
        }
    }

    @Test
    fun `get route som støtter on-behalf-of gir ikke tilgang med client credentials token`() {
        val randomUuid = UUID.randomUUID()
        fakes.gittTilgangTilSak(randomUuid.toString(), true)
        assertThrows<ManglerTilgangException> {
            runBlocking { m2mGet<Saksinfo>(base("testApi/authorizedGet/$randomUuid/on-behalf-of")) }
        }
    }

    @Test
    fun `get route som støtter client-credentials med rolle gir tilgang med riktig rolle uten at tilgang-tjenesten kalles`() {
        val randomUuid = UUID.randomUUID()
        val token = generateToken(isApp = true, roles = listOf("tilgang-rolle"))
        runBlocking {
            val res = bearerGet<Saksinfo>(
                base("testApi/authorizedGet/$randomUuid/client-credentials-application-role"),
                token.token()
            )
            assertThat(res?.saksnummer).isEqualTo(randomUuid)
        }
    }

    @Test
    fun `get route som støtter client-credentials med rolle gir ikke tilgang med feil rolle`() {
        val randomUuid = UUID.randomUUID()
        val token = generateToken(isApp = true, roles = listOf("feil-rolle"))
        assertThrows<ManglerTilgangException> {
            runBlocking {
                bearerGet<Saksinfo>(
                    base("testApi/authorizedGet/$randomUuid/client-credentials-application-role"),
                    token.token()
                )
            }
        }
    }

    @Test
    fun `post route kan sjekke tilgang til person`() {
        val person = "234"
        fakes.gittTilgangTilPerson(person, true)
        runBlocking {
            val res = oboPost<Personinfo, Personinfo>(
                base("testApi/person/post"),
                Personinfo(person),
                generateToken(isApp = false)
            )
            assertThat(res?.personIdent).isEqualTo("234")
        }
        val person2 = "123456"
        assertThrows<ManglerTilgangException> {
            runBlocking {
                oboPost<Personinfo, Personinfo>(
                    base("testApi/person/post"),
                    Personinfo(person2),
                    generateToken(isApp = false)
                )
            }
        }
    }

    @Test
    fun `get route for grunnlag setter tilgang til operasjon som attributt`() {
        val referanse = UUID.randomUUID()
        fakes.gittTilgangTilBehandling(referanse, true)
        fakes.gittTilgangTilBehandlingIKontekst(referanse, mutableMapOf(Operasjon.SAKSBEHANDLE to true))
        runBlocking {
            val res = oboGet<Boolean>(base("testApi/getGrunnlag/$referanse"), generateToken(isApp = false))
            assertThat(res).isTrue()
        }
    }

    @Test
    fun `get route for grunnlag med påkrevd rolle setter tilgang til operasjon som attributt`() {
        val referanse = UUID.randomUUID()
        fakes.gittTilgangTilBehandling(referanse, true)
        fakes.gittTilgangTilBehandlingIKontekst(referanse, mutableMapOf(Operasjon.SAKSBEHANDLE to true))
        runBlocking {
            val res = oboGet<Boolean>(base("testApi/getGrunnlag-rolle/$referanse"), generateToken(isApp = false))
            assertThat(res).isTrue()
        }
    }

    @Test
    fun `get route som støtter on-behalf-of og client-credentials gir tilgang med on-behalf-of token og client-credentials token`() {
        val randomUuid = UUID.randomUUID()
        fakes.gittTilgangTilSak(randomUuid.toString(), true)
        runBlocking {
            val res1 = oboGet<Saksinfo>(
                base("testApi/authorizedGet/$randomUuid/client-credentials-and-on-behalf-of"),
                generateToken(isApp = false)
            )
            val token = generateToken(isApp = true, roles = listOf("tilgang-rolle"))
            val res2 = bearerGet<Saksinfo>(
                base("testApi/authorizedGet/$randomUuid/client-credentials-and-on-behalf-of"),
                token.token()
            )
            assertThat(res1?.saksnummer).isEqualTo(randomUuid)
            assertThat(res2?.saksnummer).isEqualTo(randomUuid)
        }
    }

    @Test
    fun `post route som støtter on-behalf-of gir tilgang med on-behalf-of token`() {
        val randomUuid = UUID.randomUUID()
        fakes.gittTilgangTilSak(randomUuid.toString(), true)
        runBlocking {
            val res = oboPost<Saksinfo, Saksinfo>(
                base("testApi/authorizedPost/on-behalf-of/saksinfo"),
                Saksinfo(randomUuid),
                generateToken(isApp = false)
            )
            assertThat(res?.saksnummer).isEqualTo(randomUuid)
        }
    }

    @Test
    fun `post route som slår opp behandlingreferanse basert på annen referanse`() {
        val enAnnenReferanse = UUID.randomUUID()
        val behandlingReferanse = UUID.randomUUID()
        fakes.gittTilgangTilBehandling(behandlingReferanse, true)
        enAnnenReferanseTilbehandlingReferanse.put(enAnnenReferanse.toString(), behandlingReferanse)
        runBlocking {
            val res = oboPost<Behandlinginfo, Behandlinginfo>(
                base("testApi/authorizedPost/on-behalf-of/behandlinginfo"),
                Behandlinginfo(enAnnenReferanse.toString()),
                generateToken(isApp = false)
            )
            assertThat(res?.enAnnenReferanse).isEqualTo(enAnnenReferanse.toString())
        }
    }

    @Test
    fun `post route som støtter client-credentials gir tilgang med client-credentials token uten å ha en tilgang-referanse`() {
        val randomUuid = UUID.randomUUID()
        val token = generateToken(isApp = true, roles = listOf("tilgang-rolle"))
        runBlocking {
            val res = bearerPost<IngenReferanse, IngenReferanse>(
                base("testApi/authorizedPost/client-credentials-application-role"),
                IngenReferanse(randomUuid.toString()),
                token.token()
            )
            assertThat(res?.noe).isEqualTo(randomUuid.toString())
        }
    }

    @Test
    fun `post som støtter on-behalf-of token og client-credentials gir tilgang med on-behalf-of token og client-credentials token`() {
        val saksnummer = UUID.randomUUID()
        fakes.gittTilgangTilSak(saksnummer.toString(), true)
        runBlocking {
            val res1 = oboPost<Saksinfo, Saksinfo>(
                base("testApi/authorizedPost/client-credentials-and-on-behalf-of"),
                Saksinfo(saksnummer),
                generateToken(isApp = false)
            )
            val token = generateToken(isApp = true, roles = listOf("tilgang-rolle"))
            val res2 = bearerPost<Saksinfo, Saksinfo>(
                base("testApi/authorizedPost/client-credentials-and-on-behalf-of"),
                Saksinfo(saksnummer),
                token.token()
            )
            assertThat(res1?.saksnummer).isEqualTo(saksnummer)
            assertThat(res2?.saksnummer).isEqualTo(saksnummer)
        }
    }

    @Test
    fun `post som støtter client-credentials med rolle gir tilgang med riktig rolle`() {
        val randomUuid = UUID.randomUUID()
        val token = generateToken(isApp = true, roles = listOf("tilgang-rolle"))
        runBlocking {
            val res = bearerPost<IngenReferanse, IngenReferanse>(
                base("testApi/authorizedPost/client-credentials-application-role"),
                IngenReferanse(randomUuid.toString()),
                token.token()
            )
            assertThat(res?.noe).isEqualTo(randomUuid.toString())
        }
    }

    @Test
    fun `post som støtter client-credentials med rolle gir ikke tilgang med feil rolle`() {
        val randomUuid = UUID.randomUUID()
        val token = generateToken(isApp = true, roles = listOf("feil-rolle"))
        assertThrows<ManglerTilgangException> {
            runBlocking {
                bearerPost<IngenReferanse, IngenReferanse>(
                    base("testApi/authorizedPost/client-credentials-application-role"),
                    IngenReferanse(randomUuid.toString()),
                    token.token()
                )
            }
        }
    }

    @Test
    fun `post støtter path params med mapping fra annen referanse til journalpostId`() {
        val behandlingsRef = "123"
        val journalpostId = 456L
        fakes.gittTilgangTilJournalpost(journalpostId, true)
        runBlocking {
            val res = oboPost<IngenReferanse, IngenReferanse>(
                base("testApi/pathForPost/resolve/journalpost/$behandlingsRef"),
                IngenReferanse("test"),
                generateToken(isApp = false)
            )
            assertThat(res?.noe).isEqualTo("test")
        }
    }

    @Test
    fun `post støtter path params med mapping fra annen referanse til behandlingreferanse`() {
        val enAnnenReferanse = UUID.randomUUID()
        val behandlingReferanse = UUID.randomUUID()
        fakes.gittTilgangTilBehandling(behandlingReferanse, true)
        enAnnenReferanseTilbehandlingReferanse.put(enAnnenReferanse.toString(), behandlingReferanse)
        runBlocking {
            val res = oboPost<IngenReferanse, IngenReferanse>(
                base("testApi/pathForPost/resolve/behandlingreferanse/$enAnnenReferanse"),
                IngenReferanse("test"),
                generateToken(isApp = false)
            )
            assertThat(res?.noe).isEqualTo("test")
        }
    }

    @Test
    fun `skal auditlogge - path resolver`() {
        runBlocking {
            val randomUuid = UUID.randomUUID()
            fakes.gittTilgangTilSak(randomUuid.toString(), true)

            val logger = LoggerFactory.getLogger(TILGANG_PLUGIN) as Logger
            val appender = LogCaptureAppender()
            appender.start()
            logger.addAppender(appender)

            val res =
                oboGet<Saksinfo>(base("testApi/authorizedGet/$randomUuid/on-behalf-of"), generateToken(isApp = false))
            assertThat(res?.saksnummer).isEqualTo(randomUuid)

            val messages = appender.getLoggedMessages()
            val expected =
                messages.first { it.contains("CEF:0|Kelvin|behandlingsflyt|1.0|audit:access|Auditlogg|INFO|flexString1=Permit request=/testApi/authorizedGet/$randomUuid/on-behalf-of duid=12345678901 flexString1Label=Decision end=") }
            assertNotNull(expected)
            assertTrue(expected.contains("suid=Lokalsaksbehandler"))

            logger.detachAppender(appender)
        }
    }

    @Test
    fun `skal auditlogge - body resolver`() {
        runBlocking {
            val randomUuid = UUID.randomUUID()
            fakes.gittTilgangTilSak(randomUuid.toString(), true)

            val logger = LoggerFactory.getLogger(TILGANG_PLUGIN) as Logger
            val appender = LogCaptureAppender()
            appender.start()
            logger.addAppender(appender)

            val res = oboPost<RequestMedAuditResolver, RequestMedAuditResolver>(
                base("testApi/authorizedPost/med-audit-resolver"),
                RequestMedAuditResolver(saksreferanse = randomUuid),
                generateToken(isApp = false)
            )
            assertThat(res?.saksreferanse).isEqualTo(randomUuid)

            val messages = appender.getLoggedMessages()
            val expected =
                messages.first { it.contains("CEF:0|Kelvin|behandlingsflyt|1.0|audit:access|Auditlogg|INFO|flexString1=Permit request=/testApi/authorizedPost/med-audit-resolver duid=12345678901 flexString1Label=Decision end=") }
            assertNotNull(expected)
            assertTrue(expected.contains("suid=Lokalsaksbehandler"))

            logger.detachAppender(appender)
        }
    }

    @Test
    fun `Maskin-til-maskin`() {
        val randomUuid = UUID.randomUUID()
        val token = generateToken(isApp = true, roles = listOf("tilgang-rolle"))
        runBlocking {
            val res = bearerGet<Saksinfo>(
                base("testApi/authorizedGet/$randomUuid/application-role-machine-to-machine"),
                token.token()
            )
            assertThat(res?.saksnummer).isEqualTo(randomUuid)
        }
    }

    @Test
    fun `get route maskin-til-maskin gir ikke tilgang med feil rolle`() {
        val randomUuid = UUID.randomUUID()
        val token = generateToken(isApp = true, roles = listOf("feil-rolle"))
        assertThrows<ManglerTilgangException> {
            runBlocking {
                bearerGet<Saksinfo>(
                    base("testApi/authorizedGet/$randomUuid/application-role-machine-to-machine"),
                    token.token()
                )
            }
        }
    }

    @Test
    fun `get sak med påkrevdRolle gir tilgang`() {
        val randomUuid = UUID.randomUUID()
        fakes.gittTilgangTilSak(randomUuid.toString(), true)
        runBlocking {
            val res = oboGet<Saksinfo>(base("testApi/paakrevdRolle/sak/$randomUuid"), generateToken(isApp = false))
            assertThat(res?.saksnummer).isEqualTo(randomUuid)
            assertThat(fakes.sistMottattSakTilgangRequest?.påkrevdRolle).isEqualTo(listOf(Rolle.BESLUTTER))
        }
    }

    @Test
    fun `get sak med påkrevdRolle gir ikke tilgang`() {
        val randomUuid = UUID.randomUUID()
        assertThrows<ManglerTilgangException> {
            runBlocking {
                oboGet<Saksinfo>(
                    base("testApi/paakrevdRolle/sak/$randomUuid"),
                    generateToken(isApp = false)
                )
            }
        }
    }

    @Test
    fun `post sak med påkrevdRolle gir tilgang`() {
        val randomUuid = UUID.randomUUID()
        fakes.gittTilgangTilSak(randomUuid.toString(), true)
        runBlocking {
            val res = oboPost<Saksinfo, Saksinfo>(
                base("testApi/paakrevdRolle/sak/post"),
                Saksinfo(randomUuid),
                generateToken(isApp = false)
            )
            assertThat(res?.saksnummer).isEqualTo(randomUuid)
            assertThat(fakes.sistMottattSakTilgangRequest?.påkrevdRolle).isEqualTo(listOf(Rolle.BESLUTTER))
        }
    }

    @Test
    fun `post sak med påkrevdRolle gir ikke tilgang`() {
        val randomUuid = UUID.randomUUID()
        assertThrows<ManglerTilgangException> {
            runBlocking {
                oboPost<Saksinfo, Saksinfo>(
                    base("testApi/paakrevdRolle/sak/post"),
                    Saksinfo(randomUuid),
                    generateToken(isApp = false)
                )
            }
        }
    }

    @Test
    fun `skal returnere json ved ikke tilgang`() {
        val randomUuid = UUID.randomUUID()
        assertThatThrownBy {
            runBlocking {
                oboGet<Saksinfo>(
                    base("testApi/authorizedGet/$randomUuid/on-behalf-of"),
                    generateToken(isApp = false)
                )
            }
        }.isInstanceOf(ManglerTilgangException::class.java)
            .extracting("body")
            .isEqualTo("{\"message\":\"Ingen tilgang\",\"code\":\"UKJENT_FEIL\"}")
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `token med autorisert azp gir tilgang via param-config`(isApp: Boolean) {
        val saksnummer = UUID.randomUUID()
        fakes.gittTilgangTilSak(saksnummer.toString(), true)
        runBlocking {
            val token = generateToken(isApp = isApp, azp = AUTHORIZED_AZP)
            val res =
                bearerGet<Saksinfo>(base("testApi/authorizedGet/$saksnummer/authorized-azps-param"), token.token())
            assertThat(res?.saksnummer).isEqualTo(saksnummer)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `token med ikke-autorisert azp gir ikke tilgang via param-config`(isApp: Boolean) {
        val saksnummer = UUID.randomUUID()
        fakes.gittTilgangTilSak(saksnummer.toString(), true)
        assertThrows<ManglerTilgangException> {
            runBlocking {
                val token = generateToken(isApp = isApp, azp = UUID.randomUUID())
                bearerGet<Saksinfo>(base("testApi/authorizedGet/$saksnummer/authorized-azps-param"), token.token())
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `token uten azp-claim gir ikke tilgang når authorizedAzps er satt via param-config`(isApp: Boolean) {
        val saksnummer = UUID.randomUUID()
        fakes.gittTilgangTilSak(saksnummer.toString(), true)
        assertThrows<ManglerTilgangException> {
            runBlocking {
                val token = generateToken(isApp = isApp, azp = null)
                bearerGet<Saksinfo>(base("testApi/authorizedGet/$saksnummer/authorized-azps-param"), token.token())
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `token med autorisert azp gir tilgang via body-config`(isApp: Boolean) {
        val saksnummer = UUID.randomUUID()
        fakes.gittTilgangTilSak(saksnummer.toString(), true)
        runBlocking {
            val token = generateToken(isApp = isApp, azp = AUTHORIZED_AZP)
            val res = bearerPost<Saksinfo, Saksinfo>(
                base("testApi/authorizedPost/authorized-azps-body"),
                Saksinfo(saksnummer),
                token.token()
            )
            assertThat(res?.saksnummer).isEqualTo(saksnummer)
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `token med ikke-autorisert azp gir ikke tilgang via body-config`(isApp: Boolean) {
        val saksnummer = UUID.randomUUID()
        fakes.gittTilgangTilSak(saksnummer.toString(), true)
        assertThrows<ManglerTilgangException> {
            runBlocking {
                val token = generateToken(isApp = isApp, azp = UUID.randomUUID())
                bearerPost<Saksinfo, Saksinfo>(
                    base("testApi/authorizedPost/authorized-azps-body"),
                    Saksinfo(saksnummer),
                    token.token()
                )
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `token uten azp-claim gir ikke tilgang når authorizedAzps er satt via body-config`(isApp: Boolean) {
        val saksnummer = UUID.randomUUID()
        fakes.gittTilgangTilSak(saksnummer.toString(), true)
        assertThrows<ManglerTilgangException> {
            runBlocking {
                val token = generateToken(isApp = isApp, azp = null)
                bearerPost<Saksinfo, Saksinfo>(
                    base("testApi/authorizedPost/authorized-azps-body"),
                    Saksinfo(saksnummer),
                    token.token()
                )
            }
        }
    }

    @Test
    fun `maskin-til-maskin token med autorisert azp gir tilgang`() {
        val saksnummer = UUID.randomUUID()
        runBlocking {
            val token = generateToken(isApp = true, azp = AUTHORIZED_AZP)
            val res = bearerGet<Saksinfo>(
                base("testApi/authorizedGet/$saksnummer/authorized-azps-machine-to-machine"),
                token.token()
            )
            assertThat(res?.saksnummer).isEqualTo(saksnummer)
        }
    }

    @Test
    fun `maskin-til-maskin token med ikke-autorisert azp gir ikke tilgang`() {
        val saksnummer = UUID.randomUUID()
        assertThrows<ManglerTilgangException> {
            runBlocking {
                val token = generateToken(isApp = true, azp = UUID.randomUUID())
                bearerGet<Saksinfo>(
                    base("testApi/authorizedGet/$saksnummer/authorized-azps-machine-to-machine"),
                    token.token()
                )
            }
        }
    }

    @Test
    fun `maskin-til-maskin token uten azp gir ikke tilgang`() {
        val saksnummer = UUID.randomUUID()
        assertThrows<ManglerTilgangException> {
            runBlocking {
                val token = generateToken(isApp = true, azp = null)
                bearerGet<Saksinfo>(
                    base("testApi/authorizedGet/$saksnummer/authorized-azps-machine-to-machine"),
                    token.token()
                )
            }
        }
    }

    @Test
    fun `obo token med autorisert azp gir ikke tilgang`() {
        val saksnummer = UUID.randomUUID()
        assertThrows<ManglerTilgangException> {
            runBlocking {
                val token = generateToken(isApp = false, azp = AUTHORIZED_AZP)
                val res = bearerGet<Saksinfo>(
                    base("testApi/authorizedGet/$saksnummer/authorized-azps-machine-to-machine"),
                    token.token()
                )
                assertThat(res?.saksnummer).isEqualTo(saksnummer)
            }
        }
    }
}

class LogCaptureAppender : AppenderBase<ILoggingEvent>() {
    private val events = mutableListOf<ILoggingEvent>()

    override fun append(eventObject: ILoggingEvent) {
        events.add(eventObject)
    }

    fun getLoggedMessages(): List<String> = events.map { it.formattedMessage }
}

