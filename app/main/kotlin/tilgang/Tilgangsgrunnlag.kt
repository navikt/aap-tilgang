package tilgang

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon as PostmottakDefinisjon

class Tilgangsgrunnlag(
    val callId: String,
    val ansattIdent: String,
    val brukerToken: BrukerToken,
    val roller: List<Rolle>,
    val søkerIdenter: RelevanteIdenter,
    val avklaringsbehovFraBehandlingsflyt: Definisjon?,
    val avklaringsbehovFraPostmottak: PostmottakDefinisjon?,
    val påkrevdRolle: List<Rolle>,
    val operasjoner: List<Operasjon>,
)
