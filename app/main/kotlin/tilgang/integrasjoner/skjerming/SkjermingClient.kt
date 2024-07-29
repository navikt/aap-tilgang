package tilgang.integrasjoner.skjerming

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import tilgang.SkjermingConfig
import tilgang.auth.AzureConfig


open class SkjermingClient(azureConfig: AzureConfig, private val skjermingConfig: SkjermingConfig) {
    val httpClient = HttpClient()

    open suspend fun isSkjermet(ident: String): Boolean {
        val url = "${skjermingConfig.baseUrl}$ident"
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(SkjermetDataRequestDTO(ident))
        }
        return when (response.status) {
            HttpStatusCode.OK -> response.body<Boolean>()
            else -> throw SkjermingException("Feil ved henting av skjerming for ident: ${response.status} : ${response.bodyAsText()}")
        }

    }

}

internal data class SkjermetDataRequestDTO(val personident: String)