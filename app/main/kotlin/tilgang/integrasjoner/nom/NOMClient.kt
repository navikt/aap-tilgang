package tilgang.integrasjoner.nom

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import tilgang.NOMConfig
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis

open class NOMClient(private val redis: Redis, private val nomConfig: NOMConfig, private val prometheus: PrometheusMeterRegistry) {
    val httpClient = HttpClient()

    open suspend fun personNummerTilNavIdent(søkerIdent: String): String {

        if (redis.exists(Key("nom", søkerIdent))) {
            prometheus.cacheHit("nom").increment()
            return redis[Key("nom", søkerIdent)]!!.toNavident()
        }
        prometheus.cacheMiss("nom").increment()

        val response = httpClient.post(nomConfig.baseUrl) {
            contentType(ContentType.Application.Json)
            setBody(søkerIdent)
        }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val result = response.body<NOMRespons>()
                var navIdentFraNOM = ""
                if (!result.ressurs.equals(null) && !result.ressurs.navident.equals(null)) {
                    navIdentFraNOM = result.ressurs.navident
                }
                redis.set(Key("nom", søkerIdent), navIdentFraNOM.toByteArray(), 3600)
                navIdentFraNOM
            }
            else -> throw NOMException("Feil ved henting av match for søkerIdent (${søkerIdent}) mot NOM: ${response.status} : ${response.bodyAsText()}")
        }
    }
}

fun ByteArray.toNavident(): String {
    val mapper = ObjectMapper()
    val tr = object : TypeReference<String>() {}
    return mapper.readValue(this, tr)
}

fun Boolean.toByteArray(): ByteArray {
    val mapper = ObjectMapper()
    return mapper.writeValueAsBytes(this)
}