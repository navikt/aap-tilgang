package tilgang.kontroll

import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle

class Tilgangsgrunnlag(
    val kontekst: Tilgangskontekst,
    val søkerIdenter: RelevanteIdenter,
    // Alternative roller per avklaringsbehov; alle behov må være oppfylt.
    val avklaringsbehovRoller: List<List<Rolle>> = emptyList(),
    val påkrevdRolle: List<Rolle> = emptyList(),
    val operasjoner: List<Operasjon>,
)
