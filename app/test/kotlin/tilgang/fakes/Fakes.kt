package tilgang.fakes

import io.ktor.server.engine.ConnectorType
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Fakes : AutoCloseable {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val pdl by lazy { embeddedServer(Netty, port = 0, module = { pdlFake() }) }
    private val tilgangsmaskin by lazy { embeddedServer(Netty, port = 0, module = { tilgangsmaskinFake() }) }
    private val oAuth2Server = MockOAuth2Server()

    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }

        pdl.start()
        tilgangsmaskin.start()
        oAuth2Server.start()

        setProperties()
    }

    override fun close() {
        if (!started.compareAndSet(true, false)) {
            return
        }

        pdl.stop()
        tilgangsmaskin.stop()
        oAuth2Server.shutdown()
    }

    fun getOAuth2Server(): MockOAuth2Server = oAuth2Server

    private fun setProperties() {
        Thread.currentThread().setUncaughtExceptionHandler { _, e -> log.error("Uhåndtert feil", e) }

        Runtime.getRuntime().addShutdownHook(Thread { close() })

        // Azure
        System.setProperty("azure.openid.config.token.endpoint", oAuth2Server.tokenEndpointUrl("default").toString())
        System.setProperty("azure.app.client.id", "default")
        System.setProperty("azure.app.client.secret", "default")
        System.setProperty("azure.openid.config.jwks.uri", oAuth2Server.jwksUrl("default").toString())
        System.setProperty("azure.openid.config.issuer", oAuth2Server.issuerUrl("default").toString())

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

object AzurePortHolder {
    private val azurePort = AtomicInteger(0)

    fun setPort(port: Int) {
        azurePort.set(port)
    }

    fun getPort(): Int {
        return azurePort.get()
    }
}
