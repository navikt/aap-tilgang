package tilgang.fakes

import io.ktor.server.engine.ConnectorType
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import tilgang.http.createHttpClient
import kotlin.time.Duration.Companion.seconds
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Fakes : AutoCloseable {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val texas by lazy { embeddedServer(Netty, port = 0, module = { texasFake() }) }
    private val pdl by lazy { embeddedServer(Netty, port = 0, module = { pdlFake() }) }
    private val tilgangsmaskin by lazy { embeddedServer(Netty, port = 0, module = { tilgangsmaskinFake() }) }
    private val saf by lazy { embeddedServer(Netty, port = 0, module = { safFake() }) }
    private val nom by lazy { embeddedServer(Netty, port = 0, module = { nomFake() }) }
    private val skjerming by lazy { embeddedServer(Netty, port = 0, module = { skjermingFake() }) }
    private val behandlingsflyt by lazy { embeddedServer(Netty, port = 0, module = { behandlingsflytFake() }) }
    private val msGraph by lazy { embeddedServer(Netty, port = 0, module = { msGraphFake() }) }
    private val redis = RedisTestServer()
    private val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val testHttpClient = createHttpClient(5.seconds)

    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }

        texas.start()
        pdl.start()
        tilgangsmaskin.start()
        saf.start()
        nom.start()
        skjerming.start()
        behandlingsflyt.start()
        msGraph.start()
        redis.start()

        setProperties()
    }

    override fun close() {
        if (!started.compareAndSet(true, false)) {
            return
        }

        texas.stop()
        pdl.stop()
        tilgangsmaskin.stop()
        saf.stop()
        nom.stop()
        skjerming.stop()
        behandlingsflyt.stop()
        msGraph.stop()
        redis.close()
        testHttpClient.close()
    }

    fun getRedisServer() = redis.server

    fun getRedisConfig() = redis.getConfig()

    fun getPrometheus() = meterRegistry

    fun getHttpClient() = testHttpClient

    private fun setProperties() {
        Thread.currentThread().setUncaughtExceptionHandler { _, e -> log.error("Uhåndtert feil", e) }

        Runtime.getRuntime().addShutdownHook(Thread { close() })

        // Texas
        System.setProperty("NAIS_TOKEN_ENDPOINT", "http://localhost:${texas.port()}/token")
        System.setProperty("NAIS_TOKEN_EXCHANGE_ENDPOINT", "http://localhost:${texas.port()}/token/exchange")
        System.setProperty("NAIS_TOKEN_INTROSPECTION_ENDPOINT", "http://localhost:${texas.port()}/introspect")

        // PDL
        System.setProperty("PDL_BASE_URL", "http://localhost:${pdl.port()}/graphql")
        System.setProperty("PDL_SCOPE", "pdl")

        // Tilgangsmaskinen
        System.setProperty("INTEGRASJON_TILGANGSMASKIN_URL", "http://localhost:${tilgangsmaskin.port()}")
        System.setProperty("INTEGRASJON_TILGANGSMASKIN_SCOPE", "tilgangsmaskin")

        // SAF
        System.setProperty("SAF_BASE_URL", "http://localhost:${saf.port()}/graphql")
        System.setProperty("SAF_SCOPE", "saf")

        // NOM
        System.setProperty("NOM_BASE_URL", "http://localhost:${nom.port()}/graphql")
        System.setProperty("NOM_SCOPE", "nom")

        // Skjerming
        System.setProperty("SKJERMING_BASE_URL", "http://localhost:${skjerming.port()}")
        System.setProperty("SKJERMING_SCOPE", "skjerming")

        // Behandlingsflyt
        System.setProperty("BEHANDLINGSFLYT_BASE_URL", "http://localhost:${behandlingsflyt.port()}")
        System.setProperty("BEHANDLINGSFLYT_SCOPE", "behandlingsflyt")

        // MS Graph
        System.setProperty("MS_GRAPH_BASE_URL", "http://localhost:${msGraph.port()}/")
        System.setProperty("MS_GRAPH_SCOPE", "msgraph")

        // Dummy-verdier
        System.setProperty("SKJERMEDE_PERSONER_AD", UUID.randomUUID().toString())
        System.setProperty("STRENGT_FORTROLIG_ADRESSE_AD", UUID.randomUUID().toString())
        System.setProperty("FORTROLIG_ADRESSE_AD", UUID.randomUUID().toString())
    }
}

private fun EmbeddedServer<*, *>.port(): Int {
    return runBlocking { this@port.engine.resolvedConnectors() }
        .first { it.type == ConnectorType.HTTP }
        .port
}
