package tilgang

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.tilgang.Rolle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith

class ConfigTest {
    private val rolleIdenter = Rolle.entries.associateWith { UUID.randomUUID() }

    @BeforeEach
    fun konfigurerRoller() {
        mockkStatic(::requiredConfigForKey)
        rolleIdenter.forEach { (rolle, ident) ->
            every { requiredConfigForKey("AAP_${rolle.name}") } returns ident.toString()
        }
    }

    @AfterEach
    fun ryddOpp() {
        unmockkStatic(::requiredConfigForKey)
    }

    @Test
    fun `leser alle roller og gruppeidenter fra konfigurasjonen`() {
        val config = configMedRolleoppslag()

        assertThat(config.roles.associate { it.name to it.objectId }).isEqualTo(rolleIdenter)
        Rolle.entries.forEach { rolle ->
            verify(exactly = 1) { requiredConfigForKey("AAP_${rolle.name}") }
        }
    }

    @Test
    fun `manglende rollekonfigurasjon stopper lasting`() {
        val feil = IllegalStateException("Mangler AAP_DRIFT_LES")
        every { requiredConfigForKey("AAP_DRIFT_LES") } throws feil

        val resultat = assertFailsWith<IllegalStateException> { configMedRolleoppslag() }

        assertThat(resultat).isSameAs(feil)
    }

    @Test
    fun `ugyldig gruppeident stopper lasting`() {
        every { requiredConfigForKey("AAP_DRIFT_LES") } returns "ikke-en-uuid"

        assertFailsWith<IllegalArgumentException> { configMedRolleoppslag() }
    }

    private fun configMedRolleoppslag() = Config(
        redis = mockk(),
        behandlingsflyt = mockk(),
        saf = mockk(),
        pdl = mockk(),
        msGraph = mockk(),
        tilgangsmaskin = mockk(),
        texas = mockk(),
    )
}
