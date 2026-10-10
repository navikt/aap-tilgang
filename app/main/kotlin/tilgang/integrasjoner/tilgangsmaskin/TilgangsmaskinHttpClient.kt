package tilgang.integrasjoner.tilgangsmaskin

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.serialization.jackson.jackson
import kotlin.math.pow

/**
 * Dedikert HTTP-klient for Tilgangsmaskinen, adskilt fra den delte klienten (PDL m.fl.) slik at
 * retry- og timeout-strategien kan tilpasses Tilgangsmaskinen uten å påvirke andre integrasjoner.
 */
object TilgangsmaskinHttpClient {
    // Tilgangsmaskinen er tidvis treg eller utilgjengelig. Vi starter derfor med en kort timeout
    // slik at et transient hikk feiler raskt og prøves på nytt, og dobler timeouten for hvert nye
    // forsøk slik at en reelt treg (men fungerende) tjeneste får mer tid før vi til slutt gir opp.
    // Mellom forsøkene venter vi ett fast sekund (som tjenesteteamet selv gjør). Verste tilfelle er
    // ca. 1s + 2s + 4s + 8s ventetid på timeout, pluss 1s backoff mellom hvert forsøk.
    private const val MAKS_ANTALL_RETRIES = 3
    private const val INITIELL_TIMEOUT_MS = 1_000L
    private const val MAKS_TIMEOUT_MS = 10_000L
    private const val TIMEOUT_ESKALERINGSFAKTOR = 2.0
    private const val RETRY_DELAY_MS = 1_000L

    private fun eskalertTimeoutMs(retryCount: Int): Long =
        (INITIELL_TIMEOUT_MS * TIMEOUT_ESKALERINGSFAKTOR.pow(retryCount)).toLong().coerceAtMost(MAKS_TIMEOUT_MS)

    fun create(engine: HttpClientEngine = CIO.create()): HttpClient = HttpClient(engine) {
        expectSuccess = true // Kaster exception for 4xx og 5xx svar

        // HttpRequestRetry må installeres FØR HttpTimeout for at timeout-exceptions skal fanges og
        // prøves på nytt per forsøk (jf. Ktor-dokumentasjonen for HttpTimeout).
        install(HttpRequestRetry) {
            // Tilgangssjekken er kun lesende, så det er trygt å gjenta POST-kallet.
            // Vi prøver på nytt ved 5xx, transiente nettverksfeil og timeout, men ikke ved 4xx
            // (403 er et svar, ikke en feil) og ikke ved kansellering.
            retryOnServerErrors(maxRetries = MAKS_ANTALL_RETRIES)
            retryOnException(maxRetries = MAKS_ANTALL_RETRIES, retryOnTimeout = true)
            // Fast ett sekund mellom forsøk, likt det tjenesteteamet selv bruker.
            constantDelay(millis = RETRY_DELAY_MS, randomizationMs = 0)
            modifyRequest { request ->
                // Stigende verdier
                request.timeout { requestTimeoutMillis = eskalertTimeoutMs(retryCount) }
            }
        }

        install(HttpTimeout) {
            // Timeout for første forsøk. Økes for hvert nye forsøk, se modifyRequest over.
            requestTimeoutMillis = INITIELL_TIMEOUT_MS
        }

        install(ContentNegotiation) {
            jackson {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }
}
