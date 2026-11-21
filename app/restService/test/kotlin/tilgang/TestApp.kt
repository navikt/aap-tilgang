package tilgang

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import tilgang.fakes.Fakes

fun main() {
    Fakes.start()

    embeddedServer(Netty, port = 8088) {
        api(
            config = Config(redis = Fakes.getRedisConfig()),
            redis = Fakes.getRedisServer()
        )
    }.start(wait = true)
}
