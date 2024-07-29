package tilgang

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.papsign.ktor.openapigen.OpenAPIGen
import com.papsign.ktor.openapigen.route.apiRouting
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import tilgang.routes.actuator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import tilgang.auth.AZURE
import tilgang.auth.authentication
import tilgang.enhet.EnhetService
import tilgang.geo.GeoService
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytClient
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytException
import tilgang.integrasjoner.msgraph.MsGraphClient
import tilgang.integrasjoner.msgraph.MsGraphException
import tilgang.integrasjoner.pdl.PdlException
import tilgang.integrasjoner.pdl.PdlGraphQLClient
import tilgang.regler.RegelService
import tilgang.routes.tilgang

val LOGGER: Logger = LoggerFactory.getLogger("aap-tilgang")

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e -> LOGGER.error("Uhåndtert feil", e) }
    embeddedServer(Netty, port = 8080, module = Application::api).start(wait = true)
}

fun Application.api(
    config: Config = Config(),
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val pdl = PdlGraphQLClient(config.azureConfig, config.pdlConfig)
    val msGraph = MsGraphClient(config.azureConfig, config.msGraphConfig)
    val behandlingsflyt = BehandlingsflytClient(config.azureConfig, config.behandlingsflytConfig)
    val geoService = GeoService(msGraph)
    val enhetService = EnhetService(msGraph)
    val regelService = RegelService(geoService, enhetService, pdl)

    install(MicrometerMetrics) { registry = prometheus }

    authentication(config.azureConfig)

    install(CallLogging) {
        level = Level.INFO
        logger = LOGGER
        format { call ->
            """
                URL:            ${call.request.local.uri}
                Status:         ${call.response.status()}
                Method:         ${call.request.httpMethod.value}
                User-agent:     ${call.request.headers["User-Agent"]}
                CallId:         ${call.request.header("x-callId") ?: call.request.header("nav-callId")}
            """.trimIndent()
        }
        filter { call -> call.request.path().startsWith("/actuator").not() }
    }

    install(StatusPages) {
        exception<PdlException> { call, cause ->
            LOGGER.error("Uhåndtert feil ved kall til '{}'", call.request.local.uri, cause)
            call.respondText(text = "Feil i PDL: ${cause.message}", status = HttpStatusCode.InternalServerError)
        }
        exception<MsGraphException> { call, cause ->
            LOGGER.error("Uhåndtert feil ved kall til '{}'", call.request.local.uri, cause)
            call.respondText(
                text = "Feil i Microsoft Graph: ${cause.message}",
                status = HttpStatusCode.InternalServerError
            )
        }
        exception<BehandlingsflytException> { call, cause ->
            LOGGER.error("Uhåndtert feil ved kall til '{}'", call.request.local.uri, cause)
            call.respondText(
                text = "Feil i behandlingsflyt: ${cause.message}",
                status = HttpStatusCode.InternalServerError
            )
        }
        exception<Throwable> { call, cause ->
            LOGGER.error("Uhåndtert feil ved kall til '{}'", call.request.local.uri, cause)
            call.respondText(text = "Feil i tjeneste: ${cause.message}", status = HttpStatusCode.InternalServerError)
        }
    }

    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    swaggerDoc()

    routing {
        actuator(prometheus)

        authenticate(AZURE) {
            this@routing.apiRouting {
                tilgang(behandlingsflyt, regelService, config.roles)
            }
        }
    }
}

private fun Application.swaggerDoc() {
    install(OpenAPIGen) {
        // this serves OpenAPI definition on /openapi.json
        serveOpenApiJson = true
        // this serves Swagger UI on /swagger-ui/index.html
        serveSwaggerUi = true
        info {
            title = "AAP - Tilgang"
        }
    }
}