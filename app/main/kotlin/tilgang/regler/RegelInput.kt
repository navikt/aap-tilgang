package tilgang.regler

import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle
import tilgang.Tilgangskontekst

class RegelInput(
    val kontekst: Tilgangskontekst,
    val søkerIdenter: RelevanteIdenter,
    // Alternative roller per avklaringsbehov; alle behov må være oppfylt.
    val avklaringsbehovRoller: List<List<Rolle>> = emptyList(),
    val påkrevdRolle: List<Rolle> = emptyList(),
    val operasjoner: List<Operasjon>,
)