package tilgang

import no.nav.aap.tilgang.Operasjon

interface Tilgangsvurdering {
    suspend fun vurderTilgang(input: Tilgangsgrunnlag): Map<Operasjon, Boolean>
}
