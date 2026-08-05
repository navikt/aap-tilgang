package tilgang.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import tilgang.redis.Redis

fun Routing.actuator(prometheus: PrometheusMeterRegistry, redis: Redis) {
    route("/actuator") {
        get("/metrics") {
            call.respond(prometheus.scrape())
        }
        get("/live") {
            call.respond(HttpStatusCode.OK, "live")
        }
        get("/ready") {
            if (redis.ready()) {
                call.respond(HttpStatusCode.OK, "ready")
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, "redis not ready")
            }
        }
    }
}
