package tilgang.grunnlag

import tilgang.kontroll.RelevanteIdenter

interface IdentOppslag {
    suspend fun hentIdenterForSak(saksnummer: String): RelevanteIdenter
    suspend fun hentIdenterForBehandling(behandlingsnummer: String): RelevanteIdenter
}
