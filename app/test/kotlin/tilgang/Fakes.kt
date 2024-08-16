package tilgang

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tilgang.integrasjoner.nom.NOMData
import tilgang.integrasjoner.nom.NOMRespons
import tilgang.integrasjoner.nom.Ressurs
import tilgang.redis.Redis
import java.util.*

class Fakes(azurePort: Int = 0): AutoCloseable {
    private val log: Logger = LoggerFactory.getLogger(Fakes::class.java)
    private val azure = embeddedServer(Netty, port = azurePort, module = { azureFake() }).start()
    private val nom = embeddedServer(Netty, port = 1001, module = { nomFake() }).start()

   // TODO: Fakes for alle nødvendige services + routing fakes
   // private val pdl = embeddedServer(Netty, port = 0, module = { pdlFake() }).start()

    val redis = Redis(InitTestRedis.uri)
    
    init {
        Thread.currentThread().setUncaughtExceptionHandler { _, e -> log.error("Uhåndtert feil", e) }
        // Azure
        System.setProperty("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT", "http://localhost:${azure.port()}/token")
        System.setProperty("AZURE_APP_CLIENT_ID", "tilgang")
        System.setProperty("AZURE_APP_CLIENT_SECRET", "")
        System.setProperty("AZURE_OPENID_CONFIG_JWKS_URI", "http://localhost:${azure.port()}/jwks")
        System.setProperty("AZURE_OPENID_CONFIG_ISSUER", "tilgang")

        //Roller
        System.setProperty("AAP_VEILEDER", UUID.randomUUID().toString())
        System.setProperty("AAP_SAKSBEHANDLER", UUID.randomUUID().toString())
        System.setProperty("AAP_BESLUTTER", UUID.randomUUID().toString())
        System.setProperty("AAP_LES", UUID.randomUUID().toString())
        System.setProperty("STRENGT_FORTROLIG_ADRESSE", UUID.randomUUID().toString())
        System.setProperty("FORTROLIG_ADRESSE", UUID.randomUUID().toString())
        System.setProperty("AAP_UTVIKLER", UUID.randomUUID().toString())
        System.setProperty("AAP_AVDELINGSLEDER", UUID.randomUUID().toString())

        //Egen sak
        System.setProperty("NOM_BASE_URL", "http://localhost:${nom.port()}/graphql")
        System.setProperty("NOM_SCOPE", "nom_scope")

        //PDL
        System.setProperty("PDL_BASE_URL", "http://localhost:${azure.port()}")
        System.setProperty("PDL_AUDIENCE", "pdl_audience")
        System.setProperty("PDL_SCOPE", "pdl_scope")

        //MS_GRAPH
        System.setProperty("MS_GRAPH_BASE_URL", "http://localhost:${azure.port()}")
        System.setProperty("MS_GRAPH_SCOPE", "ms_scope")

        //Behandlingsflyt
        System.setProperty("BEHANDLINGSFLYT_BASE_URL", "http://localhost:${azure.port()}")
        System.setProperty("BEHANDLINGSFLYT_SCOPE", "behandlingsflyt_scope")

        //Skjermet
        System.setProperty("SKJERMING_BASE_URL", "http://localhost:${azure.port()}")
        System.setProperty("SKJERMING_SCOPE", "skjermet_scope")

        //Redis
        System.setProperty("REDIS_URI_TILGANG", "http://localhost:${azure.port()}/graphql")
        System.setProperty("REDIS_USERNAME_TILGANG", "test")
        System.setProperty("REDIS_PASSWORD_TILGANG", "test")
    }

    fun azurePort(): Int {
        return azure.port()
    }

    override fun close() {
        azure.stop(0L, 0L)
        redis.close()
    }

    private fun NettyApplicationEngine.port(): Int =
        runBlocking { resolvedConnectors() }
            .first { it.type == ConnectorType.HTTP }
            .port

    private fun Application.azureFake() {
        install(ContentNegotiation) {
            jackson()
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                this@azureFake.log.info("AZURE :: Ukjent feil ved kall til '{}'", call.request.local.uri, cause)
                call.respond(status = HttpStatusCode.InternalServerError, message = ErrorRespons(cause.message))
            }
        }
        routing {
            post("/token") {
                val token = AzureTokenGen("tilgang", "tilgang").generate()
                call.respond(TestToken(access_token = token))
            }
            get("/jwks") {
                call.respond(AZURE_JWKS)
            }
        }
    }

    private fun Application.nomFake() {
        install(ContentNegotiation) {
            jackson()
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                this@nomFake.log.info("NOM :: Ukjent feil ved kall til '{}'", call.request.local.uri, cause)
                call.respond(status = HttpStatusCode.InternalServerError, message = ErrorRespons(cause.message))
            }
        }
        routing {
            post("/graphql") {
                call.respond(NOMRespons(
                    null,
                    NOMData(
                        Ressurs("navIdentFraNOM", "personNummerFraNOM")
                    ),
                    )
                )
            }
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
}