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
import tilgang.redis.Redis

class Fakes(azurePort: Int = 0): AutoCloseable {
    private val log: Logger = LoggerFactory.getLogger(Fakes::class.java)
    private val azure = embeddedServer(Netty, port = azurePort, module = { azureFake() }).start()
    val redis = Redis(InitTestRedis.uri)

    init {
        Thread.currentThread().setUncaughtExceptionHandler { _, e -> log.error("Uhåndtert feil", e) }
        // Azure
        System.setProperty("azure.openid.config.token.endpoint", "http://localhost:${azure.port()}/token")
        System.setProperty("azure.app.client.id", "tilgang")
        System.setProperty("azure.app.client.secret", "")
        System.setProperty("azure.openid.config.jwks.uri", "http://localhost:${azure.port()}/jwks")
        System.setProperty("azure.openid.config.issuer", "tilgang")

        // TODO: Lag fakes for disse
        System.setProperty("saf.base.url", "test")
        System.setProperty("saf.scope", "saf")
        System.setProperty("nom.scope", "nom")
        System.setProperty("nom.base.url", "test")
        System.setProperty("skjerming.scope", "skjerming")
        System.setProperty("skjerming.base.url", "test")
        System.setProperty("behandlingsflyt.scope", "behandlingsflyt")
        System.setProperty("behandlingsflyt.base.url", "test")
        System.setProperty("ms.graph.scope", "msgraph")
        System.setProperty("ms.graph.base.url", "test")
    }

    fun azurePort(): Int {
        return azure.port()
    }

    override fun close() {
        azure.stop(0L, 0L)
        redis.close()
    }

    private fun EmbeddedServer<*, *>.port(): Int {
        return runBlocking {
            this@port.engine.resolvedConnectors()
        }.first { it.type == ConnectorType.HTTP }
            .port
    }

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
                val token = AzureTokenGen("tilgangazure", "tilgang").generate()
                call.respond(TestToken(access_token = token))
            }
            get("/jwks") {
                call.respond(AZURE_JWKS)
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
