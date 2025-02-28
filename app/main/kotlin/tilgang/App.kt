package tilgang

import com.papsign.ktor.openapigen.model.info.InfoModel
import com.papsign.ktor.openapigen.route.apiRouting
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.aap.komponenter.server.AZURE
import no.nav.aap.komponenter.server.commonKtorModule
import tilgang.integrasjoner.saf.SafGraphqlClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tilgang.service.EnhetService
import tilgang.service.GeoService
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytClient
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytException
import tilgang.integrasjoner.msgraph.MsGraphClient
import tilgang.integrasjoner.msgraph.MsGraphException
import tilgang.integrasjoner.nom.NomClient
import tilgang.integrasjoner.nom.NomException
import tilgang.integrasjoner.pdl.PdlException
import tilgang.integrasjoner.pdl.PdlGraphQLClient
import tilgang.integrasjoner.saf.SafException
import tilgang.integrasjoner.skjerming.SkjermingClient
import tilgang.integrasjoner.skjerming.SkjermingException
import tilgang.metrics.uhåndtertExceptionTeller
import tilgang.redis.Redis
import tilgang.regler.RegelService
import tilgang.routes.actuator
import tilgang.routes.tilgang
import tilgang.service.AdressebeskyttelseService
import tilgang.service.SkjermingService

val LOGGER: Logger = LoggerFactory.getLogger("aap-tilgang")

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e -> LOGGER.error("Uhåndtert feil", e) }
    embeddedServer(Netty, port = 8080, module = Application::api).start(wait = true)
}

fun Application.api(
    config: Config = Config(), redis: Redis = Redis(config.redis)
) {
    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val pdl = PdlGraphQLClient(redis, prometheus)
    val msGraph = MsGraphClient(redis, prometheus)
    val behandlingsflyt = BehandlingsflytClient(redis, prometheus)
    val saf = SafGraphqlClient(redis, prometheus)
    val geoService = GeoService(msGraph)
    val enhetService = EnhetService(msGraph)
    val skjermingClient = SkjermingClient(redis, prometheus)
    val skjermingService = SkjermingService(msGraph)
    val nomClient = NomClient(redis, prometheus)
    val regelService = RegelService(
        geoService, enhetService, pdl, skjermingClient, nomClient, skjermingService, AdressebeskyttelseService(msGraph)
    )
    val tilgangService = TilgangService(saf, behandlingsflyt, regelService)

    install(StatusPages) {
        exception<PdlException> { call, cause ->
            LOGGER.error("Uhåndtert feil ved kall til '{}'", call.request.local.uri, cause)
            prometheus.uhåndtertExceptionTeller(cause.javaClass.name).increment()
            call.respondText(
                text = "Feil i PDL: ${cause.message}", status = HttpStatusCode.InternalServerError
            )
        }
        exception<MsGraphException> { call, cause ->
            LOGGER.error("Uhåndtert feil ved kall til '{}'", call.request.local.uri, cause)
            prometheus.uhåndtertExceptionTeller(cause.javaClass.name).increment()
            call.respondText(
                text = "Feil i Microsoft Graph: ${cause.message}", status = HttpStatusCode.InternalServerError
            )
        }
        exception<BehandlingsflytException> { call, cause ->
            LOGGER.error("Uhåndtert feil ved kall til '{}'", call.request.local.uri, cause)
            prometheus.uhåndtertExceptionTeller(cause.javaClass.name).increment()
            call.respondText(
                text = "Feil i behandlingsflyt: ${cause.message}", status = HttpStatusCode.InternalServerError
            )
        }
        exception<SafException> { call, cause ->
            LOGGER.error("Uhåndtert feil ved kall til '{}'", call.request.local.uri, cause)
            prometheus.uhåndtertExceptionTeller(cause.javaClass.name).increment()
            call.respondText(
                text = "Feil i SAF: ${cause.message}", status = HttpStatusCode.InternalServerError
            )
        }
        exception<NomException> { call, cause ->
            LOGGER.error("Uhåndtert feil ved kall til '{}'", call.request.local.uri, cause)
            prometheus.uhåndtertExceptionTeller(cause.javaClass.name).increment()
            call.respondText(
                text = "Feil i NOM: ${cause.message}", status = HttpStatusCode.InternalServerError
            )
        }
        exception<SkjermingException> { call, cause ->
            LOGGER.error("Uhåndtert feil ved kall til '{}'", call.request.local.uri, cause)
            prometheus.uhåndtertExceptionTeller(cause.javaClass.name).increment()
            call.respondText(
                text = "Feil i skjerming: ${cause.message}", status = HttpStatusCode.InternalServerError
            )
        }
        exception<Throwable> { call, cause ->
            LOGGER.error("Uhåndtert feil ved kall til '{}'", call.request.local.uri, cause)
            prometheus.uhåndtertExceptionTeller(cause.javaClass.name).increment()
            call.respondText(
                text = "Feil i tjeneste: ${cause.message}", status = HttpStatusCode.InternalServerError
            )
        }
    }

    commonKtorModule(
        prometheus, azureConfig = config.azureConfig, infoModel = InfoModel(title = "AAP - Tilgang")
    )

    routing {
        actuator(prometheus)

        authenticate(AZURE) {
            apiRouting {
                tilgang(tilgangService, config.roles, prometheus)
            }
        }
    }
}