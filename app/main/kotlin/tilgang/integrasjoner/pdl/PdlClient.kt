package tilgang.integrasjoner.pdl

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import tilgang.LOGGER
import tilgang.PdlConfig
import tilgang.auth.AzureAdTokenProvider
import tilgang.auth.AzureConfig
import tilgang.http.HttpClientFactory

interface IPdlGraphQLClient {
    suspend fun hentPersonBolk(personidenter: List<String>, callId: String): List<PersonResultat>?

    suspend fun hentGeografiskTilknytning(ident: String, callId: String): HentGeografiskTilknytningResult?
}

class PdlGraphQLClient(
    azureConfig: AzureConfig,
    private val pdlConfig: PdlConfig
) : IPdlGraphQLClient {
    private val httpClient = HttpClientFactory.create()
    private val azureTokenProvider = AzureAdTokenProvider(
        azureConfig,
        pdlConfig.scope
    ).also { LOGGER.info("azure scope: ${pdlConfig.scope}") }

    override suspend fun hentPersonBolk(personidenter: List<String>, callId: String): List<PersonResultat>? {
        val azureToken = azureTokenProvider.getClientCredentialToken()
        val result = query(azureToken, PdlRequest.hentPersonBolk(personidenter), callId)
        return result.getOrThrow().data?.hentPersonBolk?.map {
            PersonResultat(
                it.ident,
                it.person?.adressebeskyttelse?.map { it.gradering } ?: emptyList(),
                it.code)
        }
    }

    override suspend fun hentGeografiskTilknytning(ident: String, callId: String): HentGeografiskTilknytningResult? {
        val azureToken = azureTokenProvider.getClientCredentialToken()
        val result = query(azureToken, PdlRequest.hentGeografiskTilknytning(ident), callId)
        return result.getOrThrow().data?.hentGeografiskTilknytning
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

