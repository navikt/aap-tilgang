package tilgang.integrasjoner.pdl

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.micrometer.core.instrument.MeterRegistry
import java.net.URI
import no.nav.aap.komponenter.config.requiredConfigForKey
import tilgang.auth.TokenProvider
import tilgang.http.defaultHttpClient
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize

interface IPdlGraphQLGateway {
    suspend fun hentPersonBolk(personidenter: List<String>, callId: String): List<PersonResultat>?

    suspend fun hentGeografiskTilknytning(ident: String, callId: String): HentGeografiskTilknytningResult?
}

class PdlGraphQLGateway(
    private val redis: Redis,
    private val prometheus: MeterRegistry,
    private val httpClient: HttpClient = defaultHttpClient,
) : IPdlGraphQLGateway {
    private val baseUrl = URI.create(requiredConfigForKey("pdl.base.url"))
    private val scope = requiredConfigForKey("pdl.scope")

    override suspend fun hentPersonBolk(personidenter: List<String>, callId: String): List<PersonResultat>? {
        val cachedById = personidenter.mapNotNull { ident ->
            redis.get(Key(PERSON_BOLK_PREFIX, ident))?.deserialize<PersonResultat>()?.let { ident to it }
        }.toMap()

        val personBolkResult = cachedById.values.toList()
        prometheus.cacheHit(PERSON_BOLK_PREFIX).increment(personBolkResult.size.toDouble())

        val manglendePersonidenter = personidenter.filter { it !in cachedById }

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

    override suspend fun hentGeografiskTilknytning(ident: String, callId: String): HentGeografiskTilknytningResult? {
        redis.get(Key(GEO_PREFIX, ident))?.let {
            prometheus.cacheHit(GEO_PREFIX).increment()
            return it.deserialize()
        }
        prometheus.cacheMiss(GEO_PREFIX).increment()

        val result = query(PdlRequest.hentGeografiskTilknytning(ident), callId)
        val geoTilknytning = result.data?.hentGeografiskTilknytning
        redis.set(Key(GEO_PREFIX, ident), geoTilknytning.serialize())
        return geoTilknytning
    }

    private suspend fun query(query: PdlRequest, callId: String): PdlResponse {
        val response = httpClient.post(baseUrl.toString()) {
            bearerAuth(TokenProvider.m2mToken(scope))
            header("Accept", "application/json")
            header("Nav-Call-Id", callId)
            header("TEMA", "AAP")
            header("Behandlingsnummer", BEHANDLINGSNUMMER_AAP_SAKSBEHANDLING)
            contentType(ContentType.Application.Json)
            setBody(query)
        }.body<PdlResponse>()
        if (response.errors?.isNotEmpty() == true) {
            throw PdlQueryException("Feil ${response.errors.map { it.message }.joinToString()} ved GraphQL oppslag mot $baseUrl")
        }
        return response
    }

    companion object {
        private const val PERSON_BOLK_PREFIX = "personBolk"
        private const val GEO_PREFIX = "geografiskTilknytning"
        private const val BEHANDLINGSNUMMER_AAP_SAKSBEHANDLING = "B287"
    }
}

