package tilgang.integrasjoner.pdl

import io.micrometer.core.instrument.MeterRegistry
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.ClientConfig
import no.nav.aap.komponenter.httpklient.httpclient.Header
import no.nav.aap.komponenter.httpklient.httpclient.RestClient
import no.nav.aap.komponenter.httpklient.httpclient.post
import no.nav.aap.komponenter.httpklient.httpclient.request.PostRequest
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.azurecc.ClientCredentialsTokenProvider
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize
import java.net.URI

interface IPdlGraphQLClient {
    fun hentPersonBolk(personidenter: List<String>, callId: String): List<PersonResultat>?

    fun hentGeografiskTilknytning(ident: String, callId: String): HentGeografiskTilknytningResult?
}

class PdlGraphQLClient(
    private val redis: Redis,
    private val prometheus: MeterRegistry
) : IPdlGraphQLClient {
    private val baseUrl = URI.create(requiredConfigForKey("pdl.base.url"))
    private val clientConfig = ClientConfig(
        scope = requiredConfigForKey("pdl.scope"),
    )
    private val httpClient = RestClient(
        config = clientConfig,
        tokenProvider = ClientCredentialsTokenProvider,
        responseHandler = PdlResponseHandler(),
        prometheus = prometheus,
    )

    override fun hentPersonBolk(personidenter: List<String>, callId: String): List<PersonResultat>? {
        val personBolkResult: List<PersonResultat> = personidenter.filter {
            redis.exists(Key(PERSON_BOLK_PREFIX, it))
        }.map {
            redis[Key(PERSON_BOLK_PREFIX, it)]!!.deserialize()
        }
        prometheus.cacheHit(PERSON_BOLK_PREFIX).increment(personBolkResult.size.toDouble())

        val manglendePersonidenter = personidenter.filter {
            !redis.exists(Key(PERSON_BOLK_PREFIX, it))
        }

        if (manglendePersonidenter.isEmpty()) {
            return personBolkResult
        }

        prometheus.cacheMiss(PERSON_BOLK_PREFIX).increment(manglendePersonidenter.size.toDouble())

        val result = query(PdlRequest.hentPersonBolk(manglendePersonidenter), callId)
        val nyePersoner = result.data?.hentPersonBolk?.map {
            val nyPerson = PersonResultat(
                it.ident,
                it.person?.adressebeskyttelse?.map { it.gradering } ?: emptyList(),
                it.code)
            redis.set(Key(PERSON_BOLK_PREFIX, it.ident), nyPerson.serialize(), 3600)
            nyPerson
        }?.toMutableList()
        nyePersoner?.addAll(personBolkResult)

        return nyePersoner?.toList()
    }

    override fun hentGeografiskTilknytning(ident: String, callId: String): HentGeografiskTilknytningResult? {
        if (redis.exists(Key(GEO_PREFIX, ident))) {
            prometheus.cacheHit(GEO_PREFIX).increment()
            return redis[Key(GEO_PREFIX, ident)]!!.deserialize()
        } //TODO: denne kan teknisk sett unngå token credentials, kan vi doppe den, eller burde vi dobbelsjekke
        prometheus.cacheMiss(GEO_PREFIX).increment()

        val result = query(PdlRequest.hentGeografiskTilknytning(ident), callId)
        val geoTilknytning = result.data?.hentGeografiskTilknytning
        redis.set(Key(GEO_PREFIX, ident), geoTilknytning.serialize())
        return geoTilknytning
    }

    private fun query(query: PdlRequest, callId: String): PdlResponse {
        val request = PostRequest(
            query, additionalHeaders = listOf(
                Header("Accept", "application/json"),
                Header("Nav-Call-Id", callId),
                Header("TEMA", "AAP"),
                Header("Behandlingsnummer", BEHANDLINGSNUMMER_AAP_SAKSBEHANDLING)
            )
        )
        return requireNotNull(httpClient.post(uri = baseUrl, request))
    }

    companion object {
        private const val PERSON_BOLK_PREFIX = "personBolk"
        private const val GEO_PREFIX = "geografiskTilknytning"
        private const val BEHANDLINGSNUMMER_AAP_SAKSBEHANDLING = "B287"
    }
}

