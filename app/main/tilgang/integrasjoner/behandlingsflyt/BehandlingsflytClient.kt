package tilgang.integrasjoner.behandlingsflyt

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import tilgang.BehandlingsflytConfig
import tilgang.auth.AzureAdTokenProvider
import tilgang.auth.AzureConfig
import tilgang.http.HttpClientFactory

class BehandlingsflytClient(azureConfig: AzureConfig, private val behandlingsflytConfig: BehandlingsflytConfig) {
    private val httpClient = HttpClientFactory.create()
    private val azureTokenProvider = AzureAdTokenProvider(azureConfig, behandlingsflytConfig.scope)

    suspend fun hentIdenter(currentToken: String, saksnummer: String): IdenterRespons {
        val token = azureTokenProvider.getOnBehalfOfToken(currentToken)
        val respons = httpClient.get("${behandlingsflytConfig.baseUrl}/${saksnummer}/identer") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
        }

        return when (respons.status) {
            HttpStatusCode.OK -> respons.body()
            else -> throw BehandlingsflytException("Feil ved henting av identer fra behandlingsflyt: ${respons.status} : ${respons.bodyAsText()}")
        }
    }

    data class IdenterRespons(val identer: List<String>)
}