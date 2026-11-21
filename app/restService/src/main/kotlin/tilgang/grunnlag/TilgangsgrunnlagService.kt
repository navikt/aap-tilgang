package tilgang.grunnlag

import java.util.UUID
import no.nav.aap.tilgang.RelevanteIdenter
import org.slf4j.Logger
import tilgang.kontroll.ManglerSøkerIdentException

class TilgangsgrunnlagService(
    private val identOppslag: IdentOppslag,
    private val journalpostOppslag: JournalpostOppslag,
    private val log: Logger,
) {
    suspend fun hentIdenterForSak(
        saksnummer: String,
        relevanteIdenter: RelevanteIdenter? = null,
    ): RelevanteIdenter = relevanteIdenter ?: identOppslag.hentIdenterForSak(saksnummer)

    suspend fun hentIdenterForBehandling(
        behandlingsreferanse: UUID,
        relevanteIdenter: RelevanteIdenter? = null,
    ): RelevanteIdenter =
        relevanteIdenter ?: identOppslag.hentIdenterForBehandling(behandlingsreferanse.toString())

    suspend fun hentJournalpostInfo(journalpostId: Long, callId: String): JournalpostInfo =
        journalpostOppslag.hentJournalpostInfo(journalpostId, callId)

    suspend fun finnIdenterForJournalpost(journalpost: JournalpostInfo): RelevanteIdenter {
        val saksnummer = journalpost.saksnummer
        log.info("Finner identer på journalpost med saksnummer $saksnummer.")
        if (saksnummer != null) {
            val identer = identOppslag.hentIdenterForSak(saksnummer)
            if (identer.søker.isEmpty()) {
                throw ManglerSøkerIdentException("Fant ingen søkeridenter for sak $saksnummer")
            }
            return identer
        } else {
            val søkerIdent = journalpost.søkerIdent
                ?: throw ManglerSøkerIdentException("Fant ingen ident på søker i journalposten")
            return RelevanteIdenter(søker = listOf(søkerIdent), barn = emptyList())
        }
    }
}
