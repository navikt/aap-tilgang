package tilgang.integrasjoner.nom

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.slf4j.LoggerFactory
import tilgang.LOGGER
import tilgang.NOMConfig
import tilgang.auth.AzureAdTokenProvider
import tilgang.auth.AzureConfig
import tilgang.http.HttpClientFactory
import tilgang.metrics.cacheHit
import tilgang.metrics.cacheMiss
import tilgang.redis.Key
import tilgang.redis.Redis
import tilgang.redis.Redis.Companion.deserialize
import tilgang.redis.Redis.Companion.serialize

interface INOMClient {
    suspend fun personNummerTilNavIdent(søkerIdent: String): String
}

private val log = LoggerFactory.getLogger(NOMClient::class.java)

open class NOMClient(
    azureConfig: AzureConfig,
    private val redis: Redis,
    private val nomConfig: NOMConfig,
    private val prometheus: PrometheusMeterRegistry
) : INOMClient {
    val httpClient = HttpClientFactory.create()
    private val azureTokenProvider = AzureAdTokenProvider(
        azureConfig,
        nomConfig.scope
    ).also { LOGGER.info("azure scope: ${nomConfig.scope}") }

    override suspend fun personNummerTilNavIdent(søkerIdent: String): String {
        val azureToken = ""// azureTokenProvider.getClientCredentialToken()

        log.info("Got token: $azureToken")

        if (redis.exists(Key(NOM_PREFIX, søkerIdent))) {
            prometheus.cacheHit(NOM_PREFIX).increment()
            return redis[Key(NOM_PREFIX, søkerIdent)]!!.deserialize()
        }

        prometheus.cacheMiss(NOM_PREFIX).increment()

        val query = NOMRequest.hentNavIdentFraPersonIdent(søkerIdent)
        val response = httpClient.post(nomConfig.baseUrl) {
            accept(ContentType.Application.Json)
            bearerAuth(azureToken)
            contentType(ContentType.Application.Json)
            setBody(query)
        }

        log.info("Got response status: ${response.status}")

        return when (response.status) {
            HttpStatusCode.OK -> {
                val result = response.body<NOMRespons>()

                if (result.errors?.isNotEmpty()!!) {
                    var errorsMedPath = ""
                    for (error in result.errors) {
                        errorsMedPath = errorsMedPath.plus(
                            "Feil ved oppslag mot NOM med respons: (${error.message}, på path (${
                                error.path.joinToString(
                                    "",
                                    "",
                                    "/"
                                )
                            })\n"
                        )
                    }
                    throw NOMException(errorsMedPath)
                }

                val navIdentFraNOM = result.data?.ressurs?.navident.orEmpty()
                log.info("Got navIdentFraNOM: $navIdentFraNOM")
                redis.set(Key(NOM_PREFIX, søkerIdent), navIdentFraNOM.serialize(), 3600)
                navIdentFraNOM
            }

            else -> throw NOMException("Feil ved henting av match for søkerIdent ($søkerIdent) mot NOM: ${response.status} : ${response.bodyAsText()}")
        }
    }

    companion object {
        private const val NOM_PREFIX = "nom"
    }
}