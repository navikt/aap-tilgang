package tilgang.fakes

import io.ktor.server.engine.ConnectorType
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Fakes : AutoCloseable {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val azure by lazy { embeddedServer(Netty, port = AzurePortHolder.getPort(), module = { azureFake() }) }
    private val texas by lazy { embeddedServer(Netty, port = 0, module = { texasFake() }) }
    private val pdl by lazy { embeddedServer(Netty, port = 0, module = { pdlFake() }) }
    private val tilgangsmaskin by lazy { embeddedServer(Netty, port = 0, module = { tilgangsmaskinFake() }) }
    private val redis = RedisTestServer()
    private val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }

        azure.start()
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

        azure.stop()
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

        // Azure
        System.setProperty("azure.openid.config.token.endpoint", "http://localhost:${azure.port()}/token")
        System.setProperty("azure.app.client.id", "tilgang")
        System.setProperty("azure.app.client.secret", "")
        System.setProperty("azure.openid.config.jwks.uri", "http://localhost:${azure.port()}/jwks")
        System.setProperty("azure.openid.config.issuer", "tilgang")

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

object AzurePortHolder {
    private val azurePort = AtomicInteger(0)

    fun setPort(port: Int) {
        azurePort.set(port)
    }

    fun getPort(): Int {
        return azurePort.get()
    }
}
