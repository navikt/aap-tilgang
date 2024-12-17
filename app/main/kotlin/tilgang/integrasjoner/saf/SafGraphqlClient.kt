package no.nav.aap.postmottak.saf.graphql

import SafResponseHandler
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import org.slf4j.LoggerFactory
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize
import java.net.URI

class SafGraphqlClient(
    private val redis: Redis,
    private val prometheus: PrometheusMeterRegistry
) {
    private val log = LoggerFactory.getLogger(SafGraphqlClient::class.java)

    private val baseUrl = URI.create(requiredConfigForKey("saf.base.url"))
    private val config = ClientConfig(
        scope = requiredConfigForKey("saf.scope")
    )

    private val httpClient = RestClient(
        config = config,
        tokenProvider = ClientCredentialsTokenProvider,
        responseHandler = SafResponseHandler()
    )
    
    companion object {
        private const val JOURNALPOST_PREFIX = "journalpost"
    }

    fun hentJournalpostInfo(journalpostId: Long, callId: String): SafJournalpost {
        if (redis.exists(Key(JOURNALPOST_PREFIX, journalpostId.toString()))) {
            prometheus.cacheHit(JOURNALPOST_PREFIX).increment()
            return redis[Key(JOURNALPOST_PREFIX, journalpostId.toString())]!!.deserialize()
        }
        prometheus.cacheMiss(JOURNALPOST_PREFIX).increment()

        val request = SafRequest.hentJournalpost(journalpostId)
        val response = runBlocking { query(request, callId) }

        val journalpost: SafJournalpost =
            response.getOrThrow().data?.journalpost ?: error("Fant ikke journalpost for $journalpostId")
        redis.set(Key(JOURNALPOST_PREFIX, journalpostId.toString()), journalpost.serialize())

        if (journalpost.bruker?.type != BrukerIdType.FNR) {
            log.warn("Journalpost ${journalpostId} har ikke personident")
        }

        return journalpost
    }

    private fun query(query: SafRequest, callId: String): Result<SafRespons> {
        val request = PostRequest(
            query, additionalHeaders = listOf(
                Header("Accept", "application/json"),
                Header("Nav-Call-Id", callId),


                )
        )
        return requireNotNull(httpClient.post(baseUrl, request))
    }
}