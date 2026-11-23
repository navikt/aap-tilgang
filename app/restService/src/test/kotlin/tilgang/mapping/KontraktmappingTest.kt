package tilgang.mapping

import no.nav.aap.tilgang.Operasjon as KontraktOperasjon
import no.nav.aap.tilgang.Rolle as KontraktRolle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tilgang.kontroll.Operasjon
import tilgang.kontroll.Rolle

class KontraktmappingTest {

    @Test
    fun `hver kontraktrolle mappes til domenerollen med samme navn`() {
        val mappet = KontraktRolle.entries.associate { it.name to it.tilDomene().name }

        assertThat(mappet).allSatisfy { kontrakt, domene -> assertThat(domene).isEqualTo(kontrakt) }
        assertThat(mappet.values).containsExactlyInAnyOrderElementsOf(Rolle.entries.map { it.name })
    }

    @Test
    fun `operasjoner mappes begge veier med samme navn`() {
        assertThat(KontraktOperasjon.entries.map { it.tilDomene().name })
            .containsExactlyElementsOf(KontraktOperasjon.entries.map { it.name })
        assertThat(Operasjon.entries.map { it.tilKontrakt().tilDomene() })
            .containsExactlyElementsOf(Operasjon.entries)
    }
}
