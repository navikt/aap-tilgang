package tilgang.regler

import tilgang.Rolle
import tilgang.integrasjoner.behandlingsflyt.IdenterRespons
import tilgang.routes.Operasjon

class RegelInput (
    val callId: String,
    val ident: String,
    val currentToken: String,
    val roller: List<Rolle>,
    val identer: IdenterRespons,
    val behandlingsreferanse: String?,
    val avklaringsbehov: Avklaringsbehov?,
    val operasjon: Operasjon
)

// TODO: Oppdater til nytt identer-objekt