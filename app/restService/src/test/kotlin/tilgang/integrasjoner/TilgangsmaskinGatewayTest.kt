package tilgang.integrasjoner

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.jackson
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import tilgang.auth.ITokenProvider
import tilgang.integrasjoner.tilgangsmaskin.HarTilgangFraTilgangsmaskinen
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.serialize
import tilgang.tilHabilitetsgrunnlag
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class TilgangsmaskinGatewayTest {
    private val config = mapOf(
        "INTEGRASJON_TILGANGSMASKIN_URL" to "http://tilgangsmaskin.test",
        "INTEGRASJON_TILGANGSMASKIN_SCOPE" to "tilgangsmaskin",
    )
    private val opprinneligConfig = config.mapValues { System.getProperty(it.key) }
    private val redis = mockk<Redis>()
    private val cache = mutableMapOf<Key, ByteArray>()
    private val prometheus = SimpleMeterRegistry()
    private val token = mockk<OidcToken>()
    private val tokenProvider = mockk<ITokenProvider>()
    private var responseStatus = HttpStatusCode.Forbidden
    private var responseBody = "{}"
    private var responseContentType = ContentType.Application.Json.toString()
    private val engine = MockEngine {
        respond(responseBody, responseStatus, headersOf(HttpHeaders.ContentType, responseContentType))
    }
    private val httpClient = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) {
            jackson {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }
    private lateinit var gateway: TilgangsmaskinGateway

    @BeforeEach
    fun settOpp() {
        config.forEach { (key, value) -> System.setProperty(key, value) }
        coEvery { tokenProvider.oboToken(any(), token) } returns "test-token"
        coEvery { redis[any()] } coAnswers { cache[firstArg()] }
        coEvery { redis.set(any(), any(), any()) } coAnswers {
            cache[firstArg()] = secondArg()
        }
        gateway = TilgangsmaskinGateway(redis, httpClient, prometheus)
    }

    @AfterEach
    fun ryddOpp() {
        httpClient.close()
        prometheus.close()
        opprinneligConfig.forEach { (key, value) ->
            if (value == null) System.clearProperty(key) else System.setProperty(key, value)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["kjerne", "komplett"])
    fun `tolker og mellomlagrer godkjent enkeltsvar`(regeltype: String) = runTest {
        responseStatus = HttpStatusCode.NoContent
        responseBody = ""

        val resultat = sjekkPerson(regeltype)

        assertThat(resultat.harTilgang).isTrue()
        assertThat(resultat.tilgangsmaskinAvvistResponse).isNull()
        assertThat(sjekkPerson(regeltype)).isEqualTo(resultat)
        assertThat(engine.requestHistory).hasSize(1)
        coVerify(exactly = 1) { redis.set(any(), any(), 21600L) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["kjerne", "komplett"])
    fun `tolker og mellomlagrer avslag med problem-json`(regeltype: String) = runTest {
        responseStatus = HttpStatusCode.Forbidden
        responseContentType = "application/problem+json"
        responseBody = """
            {
                "type": "urn:tilgangsmaskin:avslag",
                "title": "AVVIST_HABILITET",
                "status": 403,
                "navIdent": "Z123456",
                "begrunnelse": "Inhabil",
                "kanOverstyres": false,
                "instance": "/api/v1/$regeltype",
                "brukerIdent": "123",
                "traceId": "test-trace"
            }
        """.trimIndent()

        val resultat = sjekkPerson(regeltype)

        assertThat(resultat.harTilgang).isFalse()
        assertThat(resultat.tilgangsmaskinAvvistResponse?.title).isEqualTo("AVVIST_HABILITET")
        assertThat(sjekkPerson(regeltype)).isEqualTo(resultat)
        assertThat(engine.requestHistory).hasSize(1)
        coVerify(exactly = 1) { redis.set(any(), any(), 1800L) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["kjerne", "komplett"])
    fun `viderefører parsefeil uten å mellomlagre avslaget`(regeltype: String) = runTest {
        responseStatus = HttpStatusCode.Forbidden
        responseContentType = "application/problem+json"
        responseBody = "{"

        assertFails {
            sjekkPerson(regeltype)
        }
        coVerify(exactly = 0) { redis.set(any(), any(), any()) }

        responseStatus = HttpStatusCode.NoContent
        responseBody = ""

        assertThat(sjekkPerson(regeltype).harTilgang).isTrue()
        assertThat(engine.requestHistory).hasSize(2)
    }

    @ParameterizedTest
    @ValueSource(strings = ["kjerne", "komplett"])
    fun `feiler uten å mellomlagre når avvisningsgrunn mangler i svaret`(regeltype: String) = runTest {
        responseStatus = HttpStatusCode.Forbidden
        responseContentType = "application/problem+json"
        responseBody = """
            {
                "type": "urn:tilgangsmaskin:avslag",
                "status": 403,
                "navIdent": "Z123456",
                "begrunnelse": "Inhabil",
                "kanOverstyres": false
            }
        """.trimIndent()

        assertFails {
            sjekkPerson(regeltype)
        }
        coVerify(exactly = 0) { redis.set(any(), any(), any()) }
    }

    @ParameterizedTest
    @CsvSource("kjerne, 400", "komplett, 400", "kjerne, 500", "komplett, 500")
    fun `viderefører andre HTTP-feil fra enkeltkall uten å mellomlagre`(regeltype: String, status: Int) = runTest {
        responseStatus = HttpStatusCode.fromValue(status)

        val exception = assertFailsWith<ResponseException> {
            sjekkPerson(regeltype)
        }

        assertThat(exception.response.status.value).isEqualTo(status)
        coVerify(exactly = 0) { redis.set(any(), any(), any()) }
    }

    @Test
    fun `et tidligere mellomlagret avslag uten detaljer kan ikke godkjenne habilitet`() = runTest {
        cache[Key("tilgangsmaskinKjerne", "123Z123456")] = HarTilgangFraTilgangsmaskinen(false).serialize()

        val resultat = sjekkPerson("kjerne")

        assertFailsWith<IllegalStateException> {
            resultat.tilHabilitetsgrunnlag()
        }
        assertThat(engine.requestHistory).isEmpty()
    }

    private suspend fun sjekkPerson(regeltype: String): HarTilgangFraTilgangsmaskinen = when (regeltype) {
        "kjerne" -> gateway.harTilgangTilPersonKjerne("123", token, "Z123456")
        "komplett" -> gateway.harTilgangTilPersonKomplett("123", token, "Z123456")
        else -> error("Ukjent regeltype: $regeltype")
    }
}
