package tilgang.regler

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon as PostmottakDefinisjon
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.Rolle
import tilgang.integrasjoner.behandlingsflyt.IdenterRespons

class RegelInput (
    val callId: String,
    val ansattIdent: String,
    val currentToken: OidcToken,
    val roller: List<Rolle>,
    val søkerIdenter: IdenterRespons,
    val avklaringsbehovFraBehandlingsflyt: Definisjon?,
    val avklaringsbehovFraPostmottak: PostmottakDefinisjon?,
    val operasjoner: List<Operasjon>,
)

// TODO: Oppdater til nytt identer-objekt