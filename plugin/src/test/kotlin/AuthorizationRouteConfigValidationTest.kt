import java.util.UUID
import kotlinx.coroutines.runBlocking
import no.nav.aap.tilgang.AuthorizationBodyPathConfig
import no.nav.aap.tilgang.AuthorizationParamPathConfig
import no.nav.aap.tilgang.BehandlingTilgangRequest
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.Rolle
import no.nav.aap.tilgang.Tilgangskontekst
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AuthorizationRouteConfigValidationTest {

    @Test
    fun `AuthorizationBodyPathConfig feiler når både applicationRole og authorizedAzps er satt`() {
        val exception = assertThrows<IllegalArgumentException> {
            AuthorizationBodyPathConfig<Any>(
                operasjon = Operasjon.SE,
                applicationRole = "tilgang-rolle",
                authorizedAzps = listOf(UUID.randomUUID())
            )
        }

        assertEquals("Kan ikke sette både applicationRole og authorizedAzps", exception.message)
    }

    @Test
    fun `AuthorizationBodyPathConfig bruker påkrevd rolle fra konteksten bare når konfigurasjonen ikke har rolle`() {
        val referanse = UUID.randomUUID()
        val kontekst = { _: Unit -> Tilgangskontekst.Behandling(referanse.toString(), listOf(Rolle.BESLUTTER)) }

        val fraKontekst = runBlocking {
            AuthorizationBodyPathConfig(operasjon = Operasjon.SAKSBEHANDLE, kontekst = kontekst).tilTilgangRequest(Unit)
        }
        val fraKonfigurasjon = runBlocking {
            AuthorizationBodyPathConfig(
                operasjon = Operasjon.SAKSBEHANDLE,
                kontekst = kontekst,
                påkrevdRolle = listOf(Rolle.SAKSBEHANDLER_NASJONAL),
            ).tilTilgangRequest(Unit)
        }

        assertEquals(
            BehandlingTilgangRequest(
                behandlingsreferanse = referanse,
                påkrevdRolle = listOf(Rolle.BESLUTTER),
                operasjon = Operasjon.SAKSBEHANDLE,
            ),
            fraKontekst.tilgangRequest,
        )
        assertEquals(
            listOf(Rolle.SAKSBEHANDLER_NASJONAL),
            (fraKonfigurasjon.tilgangRequest as BehandlingTilgangRequest).påkrevdRolle,
        )
    }

    @Test
    fun `AuthorizationBodyPathConfig uten kontekst gir ingen tilgangRequest`() {
        val request = runBlocking {
            AuthorizationBodyPathConfig<Unit>(
                operasjon = Operasjon.SE,
                applicationRole = "tilgang-rolle",
                applicationsOnly = true,
            ).tilTilgangRequest(Unit)
        }

        assertNull(request.tilgangRequest)
    }

    @Test
    fun `AuthorizationParamPathConfig feiler når både applicationRole og authorizedAzps er satt`() {
        val exception = assertThrows<IllegalArgumentException> {
            AuthorizationParamPathConfig(
                applicationRole = "tilgang-rolle",
                authorizedAzps = listOf(UUID.randomUUID())
            )
        }

        assertEquals("Kan ikke sette både applicationRole og authorizedAzps", exception.message)
    }
}
