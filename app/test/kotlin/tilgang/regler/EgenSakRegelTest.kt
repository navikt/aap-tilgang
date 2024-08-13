package tilgang.regler

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tilgang.Fakes
import tilgang.NOMConfig
import tilgang.auth.AzureConfig
import tilgang.integrasjoner.nom.NOMClient
import java.net.URI

class EgenSakRegelTest {
    @Test
    fun `Saksbehandler skal ikke ha tilgang til egen sak`() {
        val navAnsattIdent = "1234"
        val navIdentFraNOM = "1234"
        assertFalse(EgenSakRegel.vurder(EgenSakInput(navAnsattIdent, navIdentFraNOM)))
    }
    @Test
    fun `Saksbehandler skal ha tilgang til andre saker`() {
        val navAnsattIdent = "1234"
        val navIdentFraNOM = "4321"
        assertTrue(EgenSakRegel.vurder(EgenSakInput(navAnsattIdent, navIdentFraNOM)))
    }
}