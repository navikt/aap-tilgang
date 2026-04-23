package tilgang

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.net.URI
import tilgang.fakes.TexasPortHolder
import tilgang.fakes.Fakes

fun main() {
    TexasPortHolder.setPort(8081)
    Fakes.start()

    embeddedServer(Netty, port = 8080) {
        api(
            Config(
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
