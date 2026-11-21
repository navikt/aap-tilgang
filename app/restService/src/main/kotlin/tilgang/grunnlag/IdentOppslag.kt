package tilgang.grunnlag

import no.nav.aap.tilgang.RelevanteIdenter

interface IdentOppslag {
    suspend fun hentIdenterForSak(saksnummer: String): RelevanteIdenter
    suspend fun hentIdenterForBehandling(behandlingsnummer: String): RelevanteIdenter
}
