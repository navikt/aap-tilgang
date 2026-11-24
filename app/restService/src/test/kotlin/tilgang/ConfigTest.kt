package tilgang

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import no.nav.aap.komponenter.config.configForKey
import no.nav.aap.tilgang.Rolle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID
import kotlin.test.assertFailsWith

class ConfigTest {
    private val rolleIdenter = Rolle.entries.associateWith { UUID.randomUUID() }

    @BeforeEach
    fun konfigurerRoller() {
        mockkStatic(::configForKey)
        rolleIdenter.forEach { (rolle, ident) ->
            every { configForKey("AAP_${rolle.name}") } returns ident.toString()
        }
    }

    @AfterEach
    fun ryddOpp() {
        unmockkStatic(::configForKey)
    }

    @Test
    fun `leser alle roller og gruppeidenter fra konfigurasjonen`() {
        val config = configMedRolleoppslag()

        assertThat(config.roles.associate { it.name to it.objectId }).isEqualTo(rolleIdenter)
        Rolle.entries.forEach { rolle ->
            verify(exactly = 1) { configForKey("AAP_${rolle.name}") }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `laster bare roller som har konfigurert gruppeident`(alleMangler: Boolean) {
        val manglendeRoller = if (alleMangler) Rolle.entries else listOf(Rolle.DRIFT_LES)
        manglendeRoller.forEach { rolle ->
            every { configForKey("AAP_${rolle.name}") } returns null
        }

        val config = configMedRolleoppslag()

        assertThat(config.roles.associate { it.name to it.objectId })
            .isEqualTo(rolleIdenter - manglendeRoller.toSet())
    }

    @ParameterizedTest
    @ValueSource(strings = ["ikke-en-uuid", ""])
    fun `ugyldig gruppeident stopper lasting`(ident: String) {
        every { configForKey("AAP_DRIFT_LES") } returns ident

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
