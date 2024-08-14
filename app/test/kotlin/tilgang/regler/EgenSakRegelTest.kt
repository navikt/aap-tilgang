package tilgang.regler

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tilgang.Fakes
import tilgang.NOMConfig
import tilgang.auth.AzureConfig
import tilgang.integrasjoner.nom.NOMClient
import tilgang.integrasjoner.nom.toByteArray
import tilgang.redis.Key
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

    /*
    @Test
    fun `Kan serialisere og deserialisere`() {
        Fakes().use{
            val redis = it.redis
            val azureConfig = AzureConfig(
                clientId = "",
                clientSecret = "",
                tokenEndpoint = URI.create("http://localhost:1234").resolve("/token").toURL(),
                jwks = URI.create("http://localhost:1234").resolve("/jwks").toURL(),
                issuer = ""
            )
            val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            val nomClient = NOMClient(azureConfig, it.redis, NOMConfig("https://nom-api.intern.dev.nav.no/graphql", "api://dev-gcp.nom.nom-api/.default"), prometheus)

            runBlocking {
                nomClient.personNummerTilNavIdent("")

                /*
                redis.set(Key("nom", "me"), emptyRespons.toByteArray(), 3600)
                if (redis.exists(Key("nom", "me"))) {
                    val foundResp = redis[Key("nom", "me")]!!.toIdenterRespons()
                    val x = 3
                }*/
            }
        }

    }

    fun ByteArray.toNavident(): String {
        val mapper = jacksonObjectMapper()
        val tr = object : TypeReference<String>() {}
        return mapper.readValue(this, tr)
    }

    fun String.toByteArray(): ByteArray {
        val mapper = jacksonObjectMapper()
        return mapper.writeValueAsBytes(this)
    }*/
}