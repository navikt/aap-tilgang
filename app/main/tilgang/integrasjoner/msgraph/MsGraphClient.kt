package tilgang.integrasjoner.msgraph

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import tilgang.MsGraphConfig
import tilgang.auth.AzureAdTokenProvider
import tilgang.auth.AzureConfig
import tilgang.http.HttpClientFactory

class MsGraphClient(azureConfig: AzureConfig, private val msGraphConfig: MsGraphConfig) {
    private val httpClient = HttpClientFactory.create()
    private val azureTokenProvider = AzureAdTokenProvider(
        azureConfig,
        msGraphConfig.scope
    )

    suspend fun hentAdGrupper(currentToken: String): MemberOf {
        val graphToken = azureTokenProvider.getOnBehalfOfToken(currentToken)

        val respons = httpClient.get("${msGraphConfig.baseUrl}/me/memberOf") {
            bearerAuth(graphToken)
            contentType(ContentType.Application.Json)
        }

        return when (respons.status) {
            HttpStatusCode.OK -> respons.body()
            else -> throw MsGraphException("Feil fra Microsoft Graph: ${respons.status} : ${respons.bodyAsText()}")
        }
    }

    data class MemberOf(
        @JsonProperty("value")
        val groups: List<Group> = emptyList()
    )

    data class Group(
        @JsonProperty("id")
        val id: String,
        @JsonProperty("mailNickname")
        val name: String
    )
}