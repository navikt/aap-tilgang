package tilgang.integrasjoner.pdl

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import tilgang.LOGGER
import tilgang.PdlConfig
import tilgang.auth.AzureAdTokenProvider
import tilgang.auth.AzureConfig
import tilgang.http.HttpClientFactory
import tilgang.redis.Key
import tilgang.redis.Redis

interface IPdlGraphQLClient {
    suspend fun hentPersonBolk(personidenter: List<String>, callId: String): List<PersonResultat>?

    suspend fun hentGeografiskTilknytning(ident: String, callId: String): HentGeografiskTilknytningResult?
}

class PdlGraphQLClient(
    azureConfig: AzureConfig,
    private val pdlConfig: PdlConfig,
    private val redis: Redis
) : IPdlGraphQLClient {
    private val httpClient = HttpClientFactory.create()
    private val azureTokenProvider = AzureAdTokenProvider(
        azureConfig,
        pdlConfig.scope
    ).also { LOGGER.info("azure scope: ${pdlConfig.scope}") }

    override suspend fun hentPersonBolk(personidenter: List<String>, callId: String): List<PersonResultat>? {
        val azureToken = azureTokenProvider.getClientCredentialToken()

        val personBolkResult = personidenter.filter {
            redis.exists(Key("personBolk", it))
        }.map {
            redis[Key("personBolk", it)]!!.toPersonResultat()
        }

        val manglendePersonidenter = personidenter.filter {
            !redis.exists(Key("personBolk", it))
        }

        val result = query(azureToken, PdlRequest.hentPersonBolk(manglendePersonidenter), callId)
        val nyePersoner = result.getOrThrow().data?.hentPersonBolk?.map {
            val nyPerson = PersonResultat(
                it.ident,
                it.person?.adressebeskyttelse?.map { it.gradering } ?: emptyList(),
                it.code)
            redis.set(Key("personBolk", it.ident), nyPerson.toByteArray(), 3600)
            nyPerson
        }?.toMutableList()
        nyePersoner?.addAll(personBolkResult)

        return nyePersoner?.toList()
    }

    fun ByteArray.toPersonResultat(): PersonResultat {
        val mapper = ObjectMapper()
        val tr = object : TypeReference<PersonResultat>() {}
        return mapper.readValue(this, tr)
    }

    fun PersonResultat.toByteArray(): ByteArray {
        val mapper = ObjectMapper()
        return mapper.writeValueAsBytes(this)
    }

    override suspend fun hentGeografiskTilknytning(ident: String, callId: String): HentGeografiskTilknytningResult {
        val azureToken = azureTokenProvider.getClientCredentialToken()

        if(redis.exists(Key("geografiskTilknytning", ident))) {
            return redis[Key("geografiskTilknytning", ident)]!!.toHentGeografiskTilknytningResult()
        } //TODO: denne kan teknisk sett unngå token credentials, kan vi doppe den, eller burde vi dobbelsjekke

        val result = query(azureToken, PdlRequest.hentGeografiskTilknytning(ident), callId)
        val geoTilknytning = result.getOrThrow().data?.hentGeografiskTilknytning
        redis.set(Key("geografiskTilknytning", ident), geoTilknytning!!.toByteArray(), 3600)
        return geoTilknytning
    }

    fun ByteArray.toHentGeografiskTilknytningResult(): HentGeografiskTilknytningResult {
        val mapper = ObjectMapper()
        val tr = object : TypeReference<HentGeografiskTilknytningResult>() {}
        return mapper.readValue(this, tr)
    }

    fun HentGeografiskTilknytningResult.toByteArray(): ByteArray {
        val mapper = ObjectMapper()
        return mapper.writeValueAsBytes(this)
    }

    private suspend fun query(accessToken: String, query: PdlRequest, callId: String): Result<PdlResponse> {
        val request = httpClient.post(pdlConfig.baseUrl) {
            accept(ContentType.Application.Json)
            header("Nav-Call-Id", callId)
            header("TEMA", "AAP")
            header("Behandlingsnummer", "B287")
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(query)
        }
        return runCatching {
            val respons = request.body<PdlResponse>()
            if (respons.errors != null) {
                throw PdlException("Feil mot PDL: ${respons.errors}")
            }
            respons
        }
    }
}

