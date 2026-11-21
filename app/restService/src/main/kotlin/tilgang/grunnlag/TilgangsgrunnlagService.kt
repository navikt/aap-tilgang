package tilgang.grunnlag

import java.util.UUID
import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle
import org.slf4j.Logger
import tilgang.kontroll.ManglerSøkerIdentException
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon as PostmottakDefinisjon

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

    fun finnAvklaringsbehovRoller(kode: String?, type: AvklaringsbehovType): List<List<Rolle>> =
        listOfNotNull(
            kode?.let {
                when (type) {
                    AvklaringsbehovType.BEHANDLING -> Definisjon.forKode(it).løsesAv.toList()
                    AvklaringsbehovType.JOURNALPOST -> PostmottakDefinisjon.forKode(it).løsesAv.toList()
                }
            }
        )

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
