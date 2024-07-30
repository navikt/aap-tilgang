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
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis


open class SkjermingClient(azureConfig: AzureConfig, private val skjermingConfig: SkjermingConfig, private val redis: Redis, private val prometheus: PrometheusMeterRegistry) {
    val httpClient = HttpClient()

    open suspend fun isSkjermet(ident: String): Boolean {
        if (redis.exists(Key("skjerming", ident))) {
            prometheus.cacheHit("skjerming").increment()
            return redis[Key("skjerming", ident)]!!.toBool()
        }
        prometheus.cacheMiss("skjerming").increment()

        val url = "${skjermingConfig.baseUrl}$ident"
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(SkjermetDataRequestDTO(ident))
        }
        return when (response.status) {
            HttpStatusCode.OK -> {
                val result = response.body<Boolean>()
                redis.set(Key("skjerming", ident), result.toByteArray(), 3600)
                result
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

internal data class SkjermetDataRequestDTO(val personident: String)