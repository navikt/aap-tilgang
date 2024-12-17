package tilgang.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

fun MeterRegistry.cacheHit(service: String): Counter = this.counter(
    "cache_hit",
    listOf(Tag.of("service", service))
)

fun MeterRegistry.cacheMiss(service: String): Counter = this.counter(
    "cache_miss",
    listOf(Tag.of("service", service))
)

fun MeterRegistry.httpCallCounter(path: String): Counter = this.counter(
    "http_call",
    listOf(Tag.of("path", path))
)

fun MeterRegistry.uhåndtertExceptionTeller(name: String): Counter = this.counter(
    "uhaandtert_exception_total",
    listOf(Tag.of("name", name))
)