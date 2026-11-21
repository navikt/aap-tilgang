package tilgang.kontroll

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TilgangServiceTest {
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `vurderer ferdig grunnlag og returnerer alle operasjonsresultatene`(alleOperasjoner: Boolean) = runTest {
        val vurdering = mockk<Tilgangsvurdering>()
        val service = TilgangService(vurdering)
        val operasjoner = if (alleOperasjoner) Operasjon.entries else listOf(Operasjon.SE)
        val grunnlag = Tilgangsgrunnlag(
            kontekst = Tilgangskontekst("ansatt", listOf(Rolle.LES), BrukerToken("test-token"), "call-id"),
            søkerIdenter = RelevanteIdenter(listOf("søker"), listOf("barn")),
            avklaringsbehov = Avklaringsbehov("kode", AvklaringsbehovType.BEHANDLING),
            påkrevdRolle = listOf(Rolle.LES),
            operasjoner = operasjoner,
        )
        val forventet = operasjoner.associateWith { it != Operasjon.SE }
        coEvery { vurdering.vurderTilgang(grunnlag) } returns forventet

        val resultat = service.vurderTilgang(grunnlag)

        assertThat(resultat).isEqualTo(forventet)
        coVerify(exactly = 1) { vurdering.vurderTilgang(grunnlag) }
    }
}
