package tilgang.integrasjoner.pdl

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import tilgang.PdlConfig
import tilgang.auth.AzureConfig
import tilgang.http.HttpClientFactory

class PdlGraphQLClient(
    azureConfig: AzureConfig,
    private val pdlConfig: PdlConfig
) {
    private val httpClient = HttpClientFactory.create()
//    private val azureTokenProvider = AzureAdTokenProvider(
//        azureConfig,
//        pdlConfig.scope
//    ).also { SECURE_LOGGER.info("azure scope: ${pdlConfig.scope}") }

    suspend fun hentPersonBolk(token: String, personidenter: List<String>, callId: String):List<PersonResultat>? {
        val result = query(token, PdlRequest.hentPersonBolk(personidenter), callId)
        return result.getOrThrow().data?.hentPersonBolk?.map { PersonResultat(it.ident, it.person?.adressebeskyttelse?.map { it.gradering }, it.code) }
    }




    private suspend fun query(accessToken: String, query: PdlRequest, callId: String): Result<PdlResponse> {
        val request = httpClient.post(pdlConfig.baseUrl) {
            accept(ContentType.Application.Json)
            header("Nav-Call-Id", callId)
            header("TEMA", "AAP")
            header("Behandlingsnummer","B287")
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

