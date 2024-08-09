package tilgang.integrasjoner.skjerming

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import tilgang.SkjermingConfig
import tilgang.auth.AzureConfig
import tilgang.integrasjoner.behandlingsflyt.IdenterRespons
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis


open class SkjermingClient(azureConfig: AzureConfig, private val skjermingConfig: SkjermingConfig, private val redis: Redis, private val prometheus: PrometheusMeterRegistry) {
    val httpClient = HttpClient()

    open suspend fun isSkjermet(identer: IdenterRespons): Boolean {
        if (redis.exists(Key("skjerming", identer.søker.first()))) {
            prometheus.cacheHit("skjerming").increment()
            return redis[Key("skjerming", identer.søker.first())]!!.toBool()
        }
        prometheus.cacheMiss("skjerming").increment()

        val url = "${skjermingConfig.baseUrl}/skjermetBulk"
        val response = httpClient.post(url) {
            val alleRelaterteSøkerIdenter = identer.søker + identer.barn
            contentType(ContentType.Application.Json)
            setBody(SkjermetDataBulkRequestDTO(alleRelaterteSøkerIdenter))
        }
        return when (response.status) {
            HttpStatusCode.OK -> {
                val result = response.body<Map<String, Boolean>>()
                val eksistererSkjermet = result.values.any {identIsSkjermet -> identIsSkjermet}

                redis.set(Key("skjerming", identer.søker.first()), eksistererSkjermet.toByteArray(), 3600)
                eksistererSkjermet
            }
            else -> throw SkjermingException("Feil ved henting av skjerming for ident: ${response.status} : ${response.bodyAsText()}")
        }

    }

}


fun ByteArray.toBool(): Boolean {
    val mapper = ObjectMapper()
    val tr = object : TypeReference<Boolean>() {}
    return mapper.readValue(this, tr)
}

fun Boolean.toByteArray(): ByteArray {
    val mapper = ObjectMapper()
    return mapper.writeValueAsBytes(this)
}

internal data class SkjermetDataBulkRequestDTO(val personIdenter: List<String>)