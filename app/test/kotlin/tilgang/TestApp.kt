package tilgang

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.net.URI
import tilgang.fakes.AzurePortHolder
import tilgang.fakes.Fakes

fun main() {
    AzurePortHolder.setPort(8081)
    Fakes.start()

    embeddedServer(Netty, port = 8080) {
        api(
            Config(
                azureConfig = no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.AzureConfig(
                    clientId = "tilgang",
                    clientSecret = "",
                    tokenEndpoint = URI.create("http://localhost:${AzurePortHolder.getPort()}").resolve("/token"),
                    jwksUri = URI.create("http://localhost:${AzurePortHolder.getPort()}").resolve("/jwks").toString(),
                    issuer = "tilgang"
                ),
                redis = RedisConfig(
                    uri = URI.create("http://127.0.0.1:6379"),
                    username = "test",
                    password = "test"
                ),
                roles = listOf(),
                // Kan fylles inn med ekte verdier om disse fakes i Fakes
            )
        )
    }.start(wait = true)
}
