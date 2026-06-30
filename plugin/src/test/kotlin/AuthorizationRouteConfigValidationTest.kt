import java.util.UUID
import no.nav.aap.tilgang.AuthorizationBodyPathConfig
import no.nav.aap.tilgang.AuthorizationParamPathConfig
import no.nav.aap.tilgang.Operasjon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AuthorizationRouteConfigValidationTest {

    @Test
    fun `AuthorizationBodyPathConfig feiler når både applicationRole og authorizedAzps er satt`() {
        val exception = assertThrows<IllegalArgumentException> {
            AuthorizationBodyPathConfig(
                operasjon = Operasjon.SE,
                applicationRole = "tilgang-rolle",
                authorizedAzps = listOf(UUID.randomUUID())
            )
        }

        assertEquals("Kan ikke sette både applicationRole og authorizedAzps", exception.message)
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
