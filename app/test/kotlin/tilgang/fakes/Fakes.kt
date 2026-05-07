package tilgang.fakes

import io.ktor.server.engine.ConnectorType
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
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
    }

    fun getRedisServer() = redis.server

    fun getRedisConfig() = redis.getConfig()

    fun getPrometheus() = meterRegistry

    private fun setProperties() {
        Thread.currentThread().setUncaughtExceptionHandler { _, e -> log.error("Uhåndtert feil", e) }

        Runtime.getRuntime().addShutdownHook(Thread { close() })

        // Texas
        System.setProperty("nais.token.endpoint", "http://localhost:${texas.port()}/token")
        System.setProperty("nais.token.exchange.endpoint", "http://localhost:${texas.port()}/token/exchange")
        System.setProperty("nais.token.introspection.endpoint", "http://localhost:${texas.port()}/introspect")

        // PDL
        System.setProperty("pdl.base.url", "http://localhost:${pdl.port()}/graphql")
        System.setProperty("pdl.scope", "pdl")

        // Tilgangsmaskinen
        System.setProperty("integrasjon.tilgangsmaskin.url", "http://localhost:${tilgangsmaskin.port()}/api/v1/kjerne")
        System.setProperty("integrasjon.tilgangsmaskin.scope", "tilgangsmaskin")

        // SAF
        System.setProperty("saf.base.url", "http://localhost:${saf.port()}/graphql")
        System.setProperty("saf.scope", "saf")

        // NOM
        System.setProperty("nom.base.url", "http://localhost:${nom.port()}/graphql")
        System.setProperty("nom.scope", "nom")

        // Skjerming
        System.setProperty("skjerming.base.url", "http://localhost:${skjerming.port()}")
        System.setProperty("skjerming.scope", "skjerming")

        // Behandlingsflyt
        System.setProperty("behandlingsflyt.base.url", "http://localhost:${behandlingsflyt.port()}")
        System.setProperty("behandlingsflyt.scope", "behandlingsflyt")

        // MS Graph
        System.setProperty("ms.graph.base.url", "http://localhost:${msGraph.port()}/")
        System.setProperty("ms.graph.scope", "msgraph")

        // Dummy-verdier
        System.setProperty("skjermede.personer.ad", UUID.randomUUID().toString())
        System.setProperty("strengt.fortrolig.adresse.ad", UUID.randomUUID().toString())
        System.setProperty("fortrolig.adresse.ad", UUID.randomUUID().toString())
    }
}

private fun EmbeddedServer<*, *>.port(): Int {
    return runBlocking { this@port.engine.resolvedConnectors() }
        .first { it.type == ConnectorType.HTTP }
        .port
}
