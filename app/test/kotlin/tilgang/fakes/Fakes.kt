package tilgang.fakes

import FakeServer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tilgang.InitTestRedis
import tilgang.redis.Redis

class Fakes(azurePort: Int = 0) : AutoCloseable {
    private val log: Logger = LoggerFactory.getLogger(Fakes::class.java)
    private val azure = FakeServer(azurePort) { azureFake() }
    private val pdl = FakeServer(module = { pdlFake() })
    val redis = Redis(InitTestRedis.uri)
    val prometheues = SimpleMeterRegistry()

    init {
        Thread.currentThread().setUncaughtExceptionHandler { _, e -> log.error("Uhåndtert feil", e) }
        // Azure
        System.setProperty("azure.openid.config.token.endpoint", "http://localhost:${azure.port()}/token")
        System.setProperty("azure.app.client.id", "tilgang")
        System.setProperty("azure.app.client.secret", "")
        System.setProperty("azure.openid.config.jwks.uri", "http://localhost:${azure.port()}/jwks")
        System.setProperty("azure.openid.config.issuer", "tilgang")

        System.setProperty("pdl.base.url", "http://localhost:${pdl.port()}/graphql")
        System.setProperty("pdl.scope", "pdl")
        
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
        azure.stop()
        pdl.stop()
        redis.close()
    }

}
