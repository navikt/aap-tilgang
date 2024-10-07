package tilgang.regler

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import tilgang.Operasjon
import tilgang.Rolle
import tilgang.integrasjoner.behandlingsflyt.IdenterRespons

class RegelInput (
    val callId: String,
    val ansattIdent: String,
    val currentToken: String,
    val roller: List<Rolle>,
    val søkerIdenter: IdenterRespons,
    val avklaringsbehov: Definisjon?,
    val operasjon: Operasjon
)

// TODO: Oppdater til nytt identer-objekt