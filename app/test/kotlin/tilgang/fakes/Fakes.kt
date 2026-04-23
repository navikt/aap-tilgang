package tilgang.fakes

import io.ktor.server.engine.ConnectorType
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Fakes : AutoCloseable {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val texas by lazy { embeddedServer(Netty, port = TexasPortHolder.getPort(), module = { texasFake() }) }
    private val pdl by lazy { embeddedServer(Netty, port = 0, module = { pdlFake() }) }
    private val tilgangsmaskin by lazy { embeddedServer(Netty, port = 0, module = { tilgangsmaskinFake() }) }
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
}

private fun EmbeddedServer<*, *>.port(): Int {
    return runBlocking { this@port.engine.resolvedConnectors() }
        .first { it.type == ConnectorType.HTTP }
        .port
}

object TexasPortHolder {
    private val azurePort = AtomicInteger(0)

    fun setPort(port: Int) {
        azurePort.set(port)
    }

    fun getPort(): Int {
        return azurePort.get()
    }
}
