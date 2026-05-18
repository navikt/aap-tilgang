package tilgang

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.response.respondText
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.net.http.HttpTimeoutException
import org.slf4j.LoggerFactory
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytException
import tilgang.integrasjoner.msgraph.MsGraphException
import tilgang.integrasjoner.nom.NomException
import tilgang.integrasjoner.pdl.PdlException
import tilgang.integrasjoner.saf.SafException
import tilgang.integrasjoner.skjerming.SkjermingException
import tilgang.metrics.uhåndtertExceptionTeller

object StatusPagesConfigHelper {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun setup(prometheus: PrometheusMeterRegistry): StatusPagesConfig.() -> Unit = {
        exception<Throwable> { call, cause ->
            prometheus.uhåndtertExceptionTeller(cause.javaClass.name).increment()

            val uri = call.request.local.uri

            when (cause) {
                is HttpRequestTimeoutException,
                is HttpTimeoutException -> {
                    logger.warn("Timeout mot '$uri'", cause)
                    call.respondText("Timeout mot: '$uri'", status = HttpStatusCode.RequestTimeout)
                }

                is PdlException -> {
                    logger.error("Uhåndtert feil ved kall til '$uri'", cause)
                    call.respondText(
                        text = "Feil i PDL: ${cause.message}", status = HttpStatusCode.InternalServerError
                    )
                }

                is MsGraphException -> {
                    logger.error("Uhåndtert feil ved kall til '$uri'", cause)
                    call.respondText(
                        text = "Feil i Microsoft Graph: ${cause.message}", status = HttpStatusCode.InternalServerError
                    )
                }

                is BehandlingsflytException -> {
                    logger.error(cause.message ?: "Uhåndtert feil ved kall til '$uri'", cause)
                    call.respondText(
                        text = "Feil i behandlingsflyt: ${cause.message}", status = HttpStatusCode.InternalServerError
                    )
                }

                is SafException -> {
                    logger.error("Uhåndtert feil ved kall til '$uri'", cause)
                    call.respondText(
                        text = "Feil i SAF: ${cause.message}", status = HttpStatusCode.InternalServerError
                    )
                }

                is NomException -> {
                    logger.error("Uhåndtert feil ved kall til '$uri'", cause)
                    call.respondText(
                        text = "Feil i NOM: ${cause.message}", status = HttpStatusCode.InternalServerError
                    )
                }

                is SkjermingException -> {
                    logger.error("Uhåndtert feil ved kall til '$uri'", cause)
                    call.respondText(
                        text = "Feil i skjerming: ${cause.message}", status = HttpStatusCode.InternalServerError
                    )
                }

                else -> {
                    logger.error("Uhåndtert feil ved kall til '$uri'", cause)
                    call.respondText(
                        text = "Feil i tjeneste: ${cause.message}",
                        status = HttpStatusCode.InternalServerError
                    )
                }
            }
        }

    }
}
