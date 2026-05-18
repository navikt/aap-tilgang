package tilgang

import com.papsign.ktor.openapigen.model.info.InfoModel
import com.papsign.ktor.openapigen.route.apiRouting
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlin.time.Duration.Companion.seconds
import no.nav.aap.komponenter.server.auth.IdentityProvider
import no.nav.aap.komponenter.server.commonKtorModule
import org.slf4j.LoggerFactory
import tilgang.http.createHttpClient
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytGateway
import tilgang.integrasjoner.msgraph.MsGraphGateway
import tilgang.integrasjoner.pdl.PdlGraphQLGateway
import tilgang.integrasjoner.saf.SafGraphqlGateway
import tilgang.integrasjoner.skjerming.SkjermingGateway
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.redis.Redis
import tilgang.regler.RegelService
import tilgang.routes.actuator
import tilgang.routes.tilgang
import tilgang.service.AdressebeskyttelseService
import tilgang.service.GeoService
import tilgang.service.SkjermingService


internal object AppConfig {
    // Matcher terminationGracePeriodSeconds for podden i Kubernetes-manifestet ("nais.yaml")
    private val kubernetesTimeout = 20.seconds

    // Tid før ktor avslutter uansett. Må være litt mindre enn `kubernetesTimeout`.
    val shutdownTimeout = kubernetesTimeout - 2.seconds

    // Tid appen får til å fullføre påbegynte requests, jobber etc. Må være mindre enn `endeligShutdownTimeout`.
    val shutdownGracePeriod = shutdownTimeout - 3.seconds

    // Vi skrur opp ktor sin default-verdi, som er "antall CPUer", satt ved -XX:ActiveProcessorCount i Dockerfile,
    // fordi appen vår er veldig I/O-bound når den venter på svar fra andre tjenester.
    private val ktorParallellitet = 4

    // Vi følger ktor sin metodikk for å regne ut tuning parametre som funksjon av parallellitet
    // https://github.com/ktorio/ktor/blob/3.3.1/ktor-server/ktor-server-core/common/src/io/ktor/server/engine/ApplicationEngine.kt#L30
    val connectionGroupSize = ktorParallellitet / 2 + 1
    val workerGroupSize = ktorParallellitet / 2 + 1

    // Vi bruker nå suspend-funksjoner for all I/O, så vi trenger ikke flere tråder enn parallelliteten.
    val callGroupSize = ktorParallellitet
}

fun main() {
    Thread.currentThread().setUncaughtExceptionHandler { _, e ->
        LoggerFactory.getLogger("aap-tilgang").error("Uhåndtert feil", e)
    }

    embeddedServer(
        Netty,
        configure = {
            connectionGroupSize = AppConfig.connectionGroupSize
            workerGroupSize = AppConfig.workerGroupSize
            callGroupSize = AppConfig.callGroupSize

            shutdownGracePeriod = AppConfig.shutdownGracePeriod.inWholeMilliseconds
            shutdownTimeout = AppConfig.shutdownTimeout.inWholeMilliseconds
            connector {
                port = System.getenv("PORT")?.toInt() ?: 8080
            }
        },
        module = Application::api
    ).start(wait = true)
}

fun Application.api(
    config: Config = Config(), redis: Redis = Redis.from(config.redis),
) {
    val httpClient = createHttpClient(10.seconds)

    val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val pdl = PdlGraphQLGateway(redis, httpClient, prometheus)
    val msGraph = MsGraphGateway(redis, httpClient, prometheus)
    val behandlingsflyt = BehandlingsflytGateway(redis, createHttpClient(timeout = 2.seconds), prometheus)
    val saf = SafGraphqlGateway(redis, httpClient, prometheus)
    val geoService = GeoService(msGraph)
    val skjermingGateway = SkjermingGateway(redis, httpClient, prometheus)
    val skjermingService = SkjermingService(msGraph)
    val tilgangsmaskinGateway = TilgangsmaskinGateway(redis, httpClient, prometheus)
    val regelService = RegelService(
        geoService, pdl, skjermingGateway, skjermingService, AdressebeskyttelseService(msGraph), tilgangsmaskinGateway
    )
    val tilgangService = TilgangService(saf, behandlingsflyt, regelService, tilgangsmaskinGateway)

    install(StatusPages, StatusPagesConfigHelper.setup(prometheus))

    commonKtorModule(
        prometheus = prometheus,
        infoModel = InfoModel(title = "AAP - Tilgang"),
        identityProvider = IdentityProvider.ENTRA_ID
    )

    routing {
        actuator(prometheus)

        authenticate(IdentityProvider.ENTRA_ID.value) {
            apiRouting {
                tilgang(tilgangService, config.roles, prometheus)
            }
        }
    }

    monitor.subscribe(ApplicationStopPreparing) { environment ->
        environment.log.info("ktor forbereder seg på å stoppe.")
    }
    monitor.subscribe(ApplicationStopping) { environment ->
        environment.log.info("ktor stopper nå å ta imot nye requester, og lar mottatte requester kjøre frem til timeout.")
    }
    monitor.subscribe(ApplicationStopped) { environment ->
        environment.log.info("ktor har fullført nedstoppingen sin. Eventuelle requester og annet arbeid som ikke ble fullført innen timeout ble avbrutt.")
    }
}