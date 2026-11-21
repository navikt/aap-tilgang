package tilgang.kontroll

import no.nav.aap.tilgang.Operasjon

class TilgangService(
    private val tilgangsvurdering: Tilgangsvurdering,
) {
    suspend fun vurderTilgang(grunnlag: Tilgangsgrunnlag): Map<Operasjon, Boolean> =
        tilgangsvurdering.vurderTilgang(grunnlag)
}
