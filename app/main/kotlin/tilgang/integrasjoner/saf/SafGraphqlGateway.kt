package tilgang.integrasjoner.saf

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.net.URI
import no.nav.aap.komponenter.config.requiredConfigForKey
import org.slf4j.LoggerFactory
import tilgang.auth.ITokenProvider
import tilgang.auth.TokenProvider
import tilgang.http.defaultHttpClient
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize

class SafGraphqlGateway(
    private val redis: Redis,
    private val prometheus: PrometheusMeterRegistry,
    private val tokenProvider: ITokenProvider = TokenProvider,
) {
    private val log = LoggerFactory.getLogger(SafGraphqlGateway::class.java)

    private val baseUrl = URI.create(requiredConfigForKey("saf.base.url"))
    private val scope = requiredConfigForKey("saf.scope")

    companion object {
        private const val JOURNALPOST_PREFIX = "journalpost"
    }

    suspend fun hentJournalpostInfo(journalpostId: Long, callId: String): SafJournalpost {
        redis.get(Key(JOURNALPOST_PREFIX, journalpostId.toString()))?.let {
            prometheus.cacheHit(JOURNALPOST_PREFIX).increment()
            return it.deserialize()
        }
        prometheus.cacheMiss(JOURNALPOST_PREFIX).increment()

        val request = SafRequest.hentJournalpost(journalpostId)
        val response = query(request, callId)

        val journalpost: SafJournalpost =
            response.data?.journalpost ?: error("Fant ikke journalpost for $journalpostId")
        redis.set(Key(JOURNALPOST_PREFIX, journalpostId.toString()), journalpost.serialize())

        if (journalpost.bruker?.type != BrukerIdType.FNR) {
            log.warn("Journalpost $journalpostId har ikke personident")
        }

        return journalpost
    }

    private suspend fun query(query: SafRequest, callId: String): SafRespons {
        val response = defaultHttpClient.post(baseUrl.toString()) {
            bearerAuth(tokenProvider.m2mToken(scope))
            header("Accept", "application/json")
            header("Nav-Call-Id", callId)
            contentType(ContentType.Application.Json)
            setBody(query)
        }.body<SafRespons>()
        if (response.errors?.isNotEmpty() == true) {
            throw SafQueryException(
                "Feil ${
                    response.errors.map { it.message }.joinToString()
                } ved GraphQL oppslag mot $baseUrl"
            )
        }
        return response
    }
}