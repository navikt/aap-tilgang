package tilgang

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    val fakes = Fakes(azurePort = 8081)

    embeddedServer(Netty, port = 8080) {
        module(fakes)
    }.start(wait = true)
}

private fun Application.module(fakes: Fakes) {
    // Setter opp virtuell sandkasse lokalt
    environment.monitor.subscribe(ApplicationStopped) { application ->
        application.environment.log.info("Server har stoppet")
        fakes.close()
        // Release resources and unsubscribe from events
        application.environment.monitor.unsubscribe(ApplicationStopped) {}
    }
}