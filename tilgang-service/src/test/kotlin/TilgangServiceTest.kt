package tilgang.kontroll

import io.mockk.Called
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tilgang.kontroll.regler.AdressebeskyttelseOppslag
import tilgang.kontroll.regler.Adressebeskyttelsesroller
import tilgang.kontroll.regler.Personkontroll

class TilgangServiceTest {
    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `avviser ønskede operasjoner uten oppslag når roller mangler`(alleOperasjoner: Boolean) = runTest {
        val adresseOppslag = mockk<AdressebeskyttelseOppslag>()
        val adresseRoller = mockk<Adressebeskyttelsesroller>()
        val personkontroll = mockk<Personkontroll>()
        val service = TilgangService(adresseOppslag, adresseRoller, personkontroll)
        val operasjoner = if (alleOperasjoner) Operasjon.entries else listOf(Operasjon.SE)
        val grunnlag = Tilgangsgrunnlag(
            kontekst = Tilgangskontekst("ansatt", emptyList(), BrukerToken("test-token"), "call-id"),
            søkerIdenter = RelevanteIdenter(listOf("søker"), listOf("barn")),
            påkrevdRolle = listOf(Rolle.LES),
            operasjoner = operasjoner,
        )

        val resultat = service.vurderTilgang(grunnlag)

        assertThat(resultat).isEqualTo(operasjoner.associateWith { false })
        coVerify {
            adresseOppslag wasNot Called
            adresseRoller wasNot Called
            personkontroll wasNot Called
        }
    }
}
