package tilgang.metrics

import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.path
import io.ktor.server.routing.RoutingCall
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag

fun MeterRegistry.cacheHit(service: String): Counter = this.counter(
    "cache_hit",
    listOf(Tag.of("service", service))
)

fun MeterRegistry.cacheMiss(service: String): Counter = this.counter(
    "cache_miss",
    listOf(Tag.of("service", service))
)

fun MeterRegistry.httpCallCounter(call: RoutingCall): Counter {
    val path = call.request.path()
    val azpName = call.principal<JWTPrincipal>()?.let {
        it.payload.claims["azp_name"]?.asString()
    } ?: ""
    return this.counter(
        "http_call",
        listOf(Tag.of("path", path), Tag.of("azp_name", azpName))
    )
}

fun MeterRegistry.uhåndtertExceptionTeller(name: String): Counter = this.counter(
    "uhaandtert_exception_total",
    listOf(Tag.of("name", name))
)

fun MeterRegistry.nektetTilgangTeller(type: String): Counter =
    this.counter("nektet_tilgang_total", listOf(Tag.of("type", type)))