package tilgang

import io.getunleash.FakeUnleash
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import tilgang.fakes.Fakes
import tilgang.unleash.UnleashService

fun main() {
    Fakes.start()

    embeddedServer(Netty, port = 8088) {
        api(
            config = Config(redis = Fakes.getRedisConfig()),
            redis = Fakes.getRedisServer(),
            unleash = UnleashService(FakeUnleash()),
        )
    }.start(wait = true)
}
