package tilgang.kontroll

import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle

class Tilgangsgrunnlag(
    val kontekst: Tilgangskontekst,
    val søkerIdenter: RelevanteIdenter,
    val avklaringsbehov: Avklaringsbehov?,
    val påkrevdRolle: List<Rolle>,
    val operasjoner: List<Operasjon>,
)
