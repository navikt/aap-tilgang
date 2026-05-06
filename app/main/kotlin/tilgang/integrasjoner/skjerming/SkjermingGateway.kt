package tilgang.integrasjoner.skjerming

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.net.URI
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.tilgang.RelevanteIdenter
import tilgang.auth.TokenProvider
import tilgang.http.defaultHttpClient
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize

open class SkjermingGateway(
    private val redis: Redis,
    private val prometheus: PrometheusMeterRegistry,
) {
    private val baseUrl = URI.create(requiredConfigForKey("skjerming.base.url"))
    private val scope = requiredConfigForKey("skjerming.scope")

    open suspend fun isSkjermet(identer: RelevanteIdenter): Boolean {
        redis.get(Key(SKJERMING_PREFIX, identer.søker.first()))?.let {
            prometheus.cacheHit(SKJERMING_PREFIX).increment()
            return it.deserialize()
        }
        prometheus.cacheMiss(SKJERMING_PREFIX).increment()

        // TODO: skal bare sjekke søkers aktive ident, historiske kan være feil
        val alleSøkersIdenter = identer.søker.distinct()
        val url = baseUrl.resolve("/skjermetBulk").toString()
        val response = defaultHttpClient.post(url) {
            bearerAuth(TokenProvider.m2mToken(scope))
            contentType(ContentType.Application.Json)
            setBody(SkjermetDataBulkRequestDTO(alleSøkersIdenter))
        }.body<Map<String, Boolean>>()

        val eksistererSkjermet = response.values.any { it }
        redis.set(Key(SKJERMING_PREFIX, identer.søker.first()), eksistererSkjermet.serialize())
        return eksistererSkjermet
    }

    companion object {
        private const val SKJERMING_PREFIX = "skjerming"
    }

}

internal data class SkjermetDataBulkRequestDTO(val personidenter: List<String>)
