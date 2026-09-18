package tilgang.regler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tilgang.integrasjoner.tilgangsmaskin.HarTilgangFraTilgangsmaskinen
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistGrunn
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinAvvistResponse

class TilgangmaskinRegelTest {
    @Test
    fun `Skal avslå når avvist med grunn inhabil`() {
        val input = HabilitetRegelInput(
            HarTilgangFraTilgangsmaskinen(
                harTilgang = false,
                TilgangsmaskinAvvistResponse(
                    title = TilgangsmaskinAvvistGrunn.AVVIST_HABILITET.toString(),
                    status = 403,
                    type = "type",
                    navIdent = "Z990883",
                    begrunnelse = "Inhabil",
                    kanOverstyres = false
                )
            )
        )
        assertFalse(HabilitetRegel.vurder(input))
    }

    @Test
    fun `Skal gi tilgang når avvist med grunn ulikt inhabil`() {
        val input = HabilitetRegelInput(
            HarTilgangFraTilgangsmaskinen(
                harTilgang = false,
                TilgangsmaskinAvvistResponse(
                    title = TilgangsmaskinAvvistGrunn.AVVIST_MANGLENDE_DATA.toString(),
                    status = 403,
                    type = "type",
                    navIdent = "Z990883",
                    begrunnelse = "Inhabil",
                    kanOverstyres = false
                )
            )
        )
        assertTrue(HabilitetRegel.vurder(input))
    }

    @Test
    fun `Skal gi tilgang når tilgangsmaskinen gir positivt svar`() {
        val input = HabilitetRegelInput(HarTilgangFraTilgangsmaskinen(true, null))
        assertTrue(HabilitetRegel.vurder(input))
    }
}