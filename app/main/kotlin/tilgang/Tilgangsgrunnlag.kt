package tilgang

import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle

class Tilgangsgrunnlag(
    val callId: String,
    val ansattIdent: String,
    val brukerToken: BrukerToken,
    val roller: List<Rolle>,
    val søkerIdenter: RelevanteIdenter,
    val avklaringsbehov: Avklaringsbehov?,
    val påkrevdRolle: List<Rolle>,
    val operasjoner: List<Operasjon>,
)
