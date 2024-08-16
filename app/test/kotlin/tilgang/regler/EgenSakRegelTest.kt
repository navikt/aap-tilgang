package tilgang.regler

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tilgang.Config
import tilgang.Fakes
import tilgang.NOMConfig
import tilgang.integrasjoner.nom.NOMClient
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

    @Test
    fun `Regel egen sak full flyt og ansatt er søker`() {
        //TODO: Gjør om til å bruke generator/vurder fra regel, slik at vi får testet alle metodene i flyten
        Fakes(azurePort = 8080).use {
            val azureConfig = Config().azureConfig
            val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

            val navAnsattIdent = "navIdentFraNOM"
            val søkerIdentPersonNummer = "12345678901"
            søkerIdentPersonNummer.reversed()

            val nomClient = NOMClient(azureConfig, it.redis, NOMConfig(), prometheus)

            runBlocking {
                val result = nomClient.personNummerTilNavIdent(søkerIdentPersonNummer, "callId")
            }
        }
    }
}