package tilgang

import com.papsign.ktor.openapigen.model.info.InfoModel
import com.papsign.ktor.openapigen.route.apiRouting
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.net.http.HttpTimeoutException
import no.nav.aap.komponenter.server.AZURE
import no.nav.aap.komponenter.server.commonKtorModule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytClient
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytException
import tilgang.integrasjoner.msgraph.MsGraphClient
import tilgang.integrasjoner.msgraph.MsGraphException
import tilgang.integrasjoner.nom.NomClient
import tilgang.integrasjoner.nom.NomException
import tilgang.integrasjoner.pdl.PdlException
import tilgang.integrasjoner.pdl.PdlGraphQLClient
import tilgang.integrasjoner.saf.SafException
import tilgang.integrasjoner.saf.SafGraphqlClient
import tilgang.integrasjoner.skjerming.SkjermingClient
import tilgang.integrasjoner.skjerming.SkjermingException
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinClient
import tilgang.metrics.uhåndtertExceptionTeller
import tilgang.redis.Redis
import tilgang.regler.RegelService
import tilgang.routes.actuator
import tilgang.routes.tilgang
import tilgang.service.AdressebeskyttelseService
import tilgang.service.EnhetService
import tilgang.service.GeoService
import tilgang.service.SkjermingService

val LOGGER: Logger = LoggerFactory.getLogger("aap-tilgang")

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e -> LOGGER.error("Uhåndtert feil", e) }

    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toInt() ?: 8080,
        module = Application::api
    ).start(wait = true)
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
    val tilgangsmaskinClient = TilgangsmaskinClient(redis, prometheus)
    val regelService = RegelService(
        geoService, enhetService, pdl, skjermingClient, nomClient, skjermingService, AdressebeskyttelseService(msGraph), tilgangsmaskinClient
    )
    val tilgangService = TilgangService(saf, behandlingsflyt, regelService, tilgangsmaskinClient)

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            prometheus.uhåndtertExceptionTeller(cause.javaClass.name).increment()

            when (cause) {
                is HttpTimeoutException -> {
                    LOGGER.warn("Timeout mot '{}'", call.request.local.uri, cause)
                    call.respondText("Timeout mot: '{}'", status = HttpStatusCode.RequestTimeout)
                }

                is PdlException -> {
                    LOGGER.error("Uhåndtert feil ved kall til '{}'", call.request.local.uri, cause)
                    call.respondText(
                        text = "Feil i PDL: ${cause.message}", status = HttpStatusCode.InternalServerError
                    )
                }

                is MsGraphException -> {
                    LOGGER.error("Uhåndtert feil ved kall til '{}'", call.request.local.uri, cause)
                    call.respondText(
                        text = "Feil i Microsoft Graph: ${cause.message}", status = HttpStatusCode.InternalServerError
                    )
                }

                is BehandlingsflytException -> {
                    LOGGER.error(cause.message ?: "Uhåndtert feil ved kall til '${call.request.local.uri}'", cause)
                    call.respondText(
                        text = "Feil i behandlingsflyt: ${cause.message}", status = HttpStatusCode.InternalServerError
                    )
                }

                is SafException -> {
                    LOGGER.error("Uhåndtert feil ved kall til '{}'", call.request.local.uri, cause)
                    call.respondText(
                        text = "Feil i SAF: ${cause.message}", status = HttpStatusCode.InternalServerError
                    )
                }

                is NomException -> {
                    LOGGER.error("Uhåndtert feil ved kall til '{}'", call.request.local.uri, cause)
                    call.respondText(
                        text = "Feil i NOM: ${cause.message}", status = HttpStatusCode.InternalServerError
                    )
                }

                is SkjermingException -> {
                    LOGGER.error("Uhåndtert feil ved kall til '{}'", call.request.local.uri, cause)
                    call.respondText(
                        text = "Feil i skjerming: ${cause.message}", status = HttpStatusCode.InternalServerError
                    )
                }

                else -> {
                    LOGGER.error("Uhåndtert feil ved kall til '{}'", call.request.local.uri, cause)
                    call.respondText(
                        text = "Feil i tjeneste: ${cause.message}",
                        status = HttpStatusCode.InternalServerError
                    )
                }
            }
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