package tilgang

import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.RelevanteIdenter
import tilgang.integrasjoner.saf.SafJournalpost
import tilgang.integrasjoner.tilgangsmaskin.BrukerOgRegeltype
import tilgang.regler.RegelInput

interface JournalpostOppslag {
    suspend fun hentJournalpostInfo(journalpostId: Long, callId: String): SafJournalpost
}

interface IdentOppslag {
    suspend fun hentIdenterForSak(saksnummer: String): RelevanteIdenter
    suspend fun hentIdenterForBehandling(behandlingsnummer: String): RelevanteIdenter
}

interface Tilgangsvurdering {
    suspend fun vurderTilgang(input: RegelInput): Map<Operasjon, Boolean>
}

interface Persontilgang {
    suspend fun harTilgangTilPerson(brukerIdent: String, token: OidcToken, callId: String?): Boolean
    suspend fun harTilganger(
        brukerIdenter: List<BrukerOgRegeltype>,
        token: OidcToken,
        callId: String?,
    ): Boolean
}
