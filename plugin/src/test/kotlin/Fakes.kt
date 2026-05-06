import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import no.nav.aap.tilgang.BehandlingTilgangRequest
import no.nav.aap.tilgang.JournalpostTilgangRequest
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.PersonTilgangRequest
import no.nav.aap.tilgang.SakTilgangRequest
import no.nav.aap.tilgang.TilgangResponse
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

internal class Fakes(val azureTokenGen: AzureTokenGen) : AutoCloseable {
    private val texas = embeddedServer(Netty, port = 0, module = { texasFake() }).start()
    private val tilgang = embeddedServer(Netty, port = 0, module = { tilgangFake() }).apply { start() }

    private fun Application.texasFake() {
        install(ContentNegotiation) {
            jackson()
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                this@texasFake.log.info("AZURE :: Ukjent feil ved kall til '{}'", call.request.local.uri, cause)
                call.respond(status = HttpStatusCode.InternalServerError, message = ErrorRespons(cause.message))
            }
        }
        routing {
            post("/token") {
                call.respond(TestToken(azureTokenGen.generate(isApp = true)))
            }
            post("/token/exchange") {
                call.respond(TestToken(azureTokenGen.generate(isApp = false)))
            }
            post("/introspect") {
                call.respond(mapOf("active" to true))
            }
        }
    }

    private val tilgangTilSak = mutableMapOf<String, Boolean>()
    var sistMottattSakTilgangRequest: SakTilgangRequest? = null
        private set

    fun gittTilgangTilSak(sak: String, tilgang: Boolean) {
        tilgangTilSak[sak] = tilgang
    }

    private val tilgangTilBehandlingIKontekst = mutableMapOf<UUID, Map<Operasjon, Boolean>>()
    fun gittTilgangTilBehandlingIKontekst(behandling: UUID, tilgangMap: Map<Operasjon, Boolean>) {
        tilgangTilBehandlingIKontekst[behandling] = tilgangMap
    }

    private val tilgangTilBehandling = mutableMapOf<UUID, Boolean>()
    fun gittTilgangTilBehandling(behandling: UUID, tilgang: Boolean) {
        tilgangTilBehandling[behandling] = tilgang
    }

    private val tilgangTilJournalpost = mutableMapOf<Long, Boolean>()
    fun gittTilgangTilJournalpost(journalpost: Long, tilgang: Boolean) {
        tilgangTilJournalpost[journalpost] = tilgang
    }

    private val tilgangTilPerson = mutableMapOf<String, Boolean>()
    fun gittTilgangTilPerson(personIdent: String, tilgang: Boolean) {
        tilgangTilPerson[personIdent] = tilgang
    }

    private fun Application.tilgangFake() {
        install(ContentNegotiation) {
            jackson()
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                this@tilgangFake.log.info(
                    "TILGANG :: Ukjent feil ved kall til '{}'",
                    call.request.local.uri,
                    cause
                )
                call.respond(status = HttpStatusCode.InternalServerError, message = ErrorRespons(cause.message))
            }
        }
        install(Authentication) {
            jwt {
                verifier(
                    JWT
                        .require(Algorithm.RSA256(PRIVATE_TEST_RSA_KEY.toRSAPublicKey()))
                        .withAudience(azureTokenGen.audience)
                        .withIssuer(azureTokenGen.issuer)
                        .build()
                )
                validate { credential -> JWTPrincipal(credential.payload) }
            }
        }
        routing {
            authenticate {

                post("/tilgang/sak") {
                    val req = call.receive<SakTilgangRequest>()
                    sistMottattSakTilgangRequest = req
                    call.respond(TilgangResponse(tilgangTilSak[req.saksnummer] == true))
                }
                post("/tilgang/behandling") {
                    val req = call.receive<BehandlingTilgangRequest>()
                    call.respond(
                        TilgangResponse(
                            tilgangTilBehandling[req.behandlingsreferanse] == true,
                            tilgangTilBehandlingIKontekst[req.behandlingsreferanse]
                        )
                    )
                }
                post("/tilgang/journalpost") {
                    val req = call.receive<JournalpostTilgangRequest>()
                    call.respond(TilgangResponse(tilgangTilJournalpost[req.journalpostId] == true))
                }
                post("tilgang/person") {
                    val req = call.receive<PersonTilgangRequest>()
                    call.respond(TilgangResponse(tilgangTilPerson[req.personIdent] == true))
                }
            }
        }
    }

    private fun EmbeddedServer<*, *>.port(): Int {
        return runBlocking {
            this@port.engine.resolvedConnectors()
        }.first { it.type == ConnectorType.HTTP }
            .port
    }

    init {
        // Texas
        System.setProperty("nais.token.endpoint", "http://localhost:${texas.port()}/token")
        System.setProperty("nais.token.exchange.endpoint", "http://localhost:${texas.port()}/token/exchange")
        System.setProperty("nais.token.introspection.endpoint", "http://localhost:${texas.port()}/introspect")
        // Tilgang
        System.setProperty("integrasjon.tilgang.url", "http://localhost:${tilgang.port()}")
        System.setProperty("integrasjon.tilgang.scope", "scope")
        System.setProperty("integrasjon.tilgang.azp", "azp")
    }

    override fun close() {
        texas.stop(0L, 0L)
    }
}

internal data class TestToken(
    val access_token: String,
    val refresh_token: String = "very.secure.token",
    val id_token: String = "very.secure.token",
    val token_type: String = "token-type",
    val scope: String? = null,
    val expires_in: Int = 3599,
)

internal class AzureTokenGen(val issuer: String, val audience: String) {

    private fun signed(claims: JWTClaimsSet): SignedJWT {
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(PRIVATE_TEST_RSA_KEY.keyID).type(JOSEObjectType.JWT).build()
        val signer = RSASSASigner(PRIVATE_TEST_RSA_KEY.toPrivateKey())
        val signedJWT = SignedJWT(header, claims)
        signedJWT.sign(signer)
        return signedJWT
    }

    private fun claims(isApp: Boolean, roles: List<String>): JWTClaimsSet {
        val builder = JWTClaimsSet
            .Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(UUID.randomUUID().toString())
            .expirationTime(LocalDateTime.now().plusHours(4).toDate())
            .claim("NAVident", "Lokalsaksbehandler")
            .claim("azp_name", "azp")

        if (isApp) {
            builder.claim("idtyp", "app")
            builder.claim("roles", roles)
        } else {
            builder.claim("groups", roles)
            builder.claim("NAVident", "Lokalsaksbehandler")
        }

        return builder.build()
    }

    private fun LocalDateTime.toDate(): Date {
        return Date.from(this.atZone(ZoneId.systemDefault()).toInstant())
    }

    fun generate(isApp: Boolean, roles: List<String> = emptyList()): String {
        return signed(claims(isApp, roles)).serialize()
    }
}

@Language("JSON")
internal const val AZURE_JWKS: String = """{
  "keys": [
    {
      "kty": "RSA",
      "d": "O4HE82G7UP-KVIryTboX-VqbxBbSo16_shQ-zIGUiHo0DVoTBJYfmRWSIx4bPT-n80imaYhohHd79UO1lqWMF-GrZdFJaYjU7yzKGc_W7Pw5QVVng9JZRlgIuz_L7Zl-q3R1gV0-FZWhRZtkhIbETl8216cBFjSrUVF04Fpv4n9dBV3ySgjfG_0MMuysAWx6gZFyP2g1IOnuCY7v32kLR9wdLWPSKFz-icm66AR5DX0hyMdUuwQ56DEBAzf6-1MqznqKiwg-whL6zcHHLdaWzj02J8bMLpeZ9PylbxdTHEWdMP6HaXNdqVMx920UnWmCVVcFOIxs53PdGnyJThVPEQ",
      "e": "AQAB",
      "use": "sig",
      "kid": "localhost-signer",
      "alg": "RS256",
      "n": "wQkxSymiZJ8k4zBTo8HhjmvMB-OZl6F1qg_ZsPXwfa8jTzxbxkicAAPKowh7T0vT_dQAR_Vhy9G6v2jkUUnlbvxULqOt395TTUEB-MBPb0gxIk9O65Ws9eRj12hWo6gDaHBuxWEEjzvVHEDAmqHs7mswoY7nkn2ktxYDPdCjKystyCyR6TCMxkOMXLt0gUfdZyGir60d4ZsGeSIV66L2_pGI0qsEELGvXCLKQe7-UceyYioxmjRs_GGl8Zd1psSiXiZYXHUYIIslZakPUPNUM5_2eFwTbwQPybhJ0WLqUxWEGfoZjyMflR0FTTo5ZLOKLZAsXCpZlR7nY_tuMNWWhw"
    }
  ]
}"""

private val PRIVATE_TEST_RSA_KEY: RSAKey = JWKSet.parse(AZURE_JWKS).getKeyByKeyId("localhost-signer") as RSAKey


data class ErrorRespons(val message: String?)
