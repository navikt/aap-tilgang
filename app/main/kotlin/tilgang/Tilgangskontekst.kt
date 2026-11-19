package tilgang

import no.nav.aap.tilgang.Rolle

class Tilgangskontekst(
    val ansattIdent: String,
    val roller: List<Rolle>,
    val brukerToken: BrukerToken,
    val callId: String,
)
