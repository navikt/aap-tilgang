package tilgang

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.tilgang.ArenasakTilgangRequest
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon as PostmottakDefinisjon
import no.nav.aap.tilgang.BehandlingTilgangRequest
import no.nav.aap.tilgang.JournalpostTilgangRequest
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.PersonTilgangRequest
import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle
import no.nav.aap.tilgang.SakTilgangRequest
import no.nav.aap.tilgang.TilbakekrevingTilgangRequest
import org.slf4j.LoggerFactory

class TilgangService(
    private val journalpostOppslag: JournalpostOppslag,
    private val identOppslag: IdentOppslag,
    private val tilgangsvurdering: Tilgangsvurdering,
    private val persontilgang: Persontilgang
) {
    private val log = LoggerFactory.getLogger(TilgangService::class.java)

    suspend fun harTilgangTilSak(
        ansattIdent: String,
        req: SakTilgangRequest,
        roller: List<Rolle>,
        token: BrukerToken,
        callId: String
    ): Boolean {
        log.info("Sjekker tilgang til sak ${req.saksnummer}")
        val identer = req.relevanteIdenter ?: identOppslag
            .hentIdenterForSak(req.saksnummer)

        val regelInput = Tilgangsgrunnlag(
            callId = callId,
            ansattIdent = ansattIdent,
            brukerToken = token,
            roller = roller,
            søkerIdenter = identer,
            avklaringsbehovFraBehandlingsflyt = null,
            avklaringsbehovFraPostmottak = null, 
            påkrevdRolle = req.påkrevdRolle ?: emptyList(),
            operasjoner = listOf(req.operasjon)
        )
        return tilgangsvurdering.vurderTilgang(regelInput)[req.operasjon] == true
    }

    suspend fun harTilgangTilBehandling(
        ansattIdent: String,
        req: BehandlingTilgangRequest,
        roller: List<Rolle>,
        token: BrukerToken,
        callId: String,
    ): Map<Operasjon, Boolean> {
        log.info("Sjekker tilgang til behandling ${req.behandlingsreferanse}")
        val identer = req.relevanteIdenter ?: identOppslag
            .hentIdenterForBehandling(req.behandlingsreferanse.toString())

        val avklaringsbehov =
            if (req.avklaringsbehovKode != null) Definisjon.forKode(req.avklaringsbehovKode!!) else null

        val regelInput = Tilgangsgrunnlag(
            callId = callId,
            ansattIdent = ansattIdent,
            brukerToken = token,
            roller = roller,
            søkerIdenter = identer,
            avklaringsbehovFraBehandlingsflyt = avklaringsbehov,
            avklaringsbehovFraPostmottak = null,
            påkrevdRolle = req.påkrevdRolle ?: emptyList(),
            operasjoner = Operasjon.entries
        )
        return tilgangsvurdering.vurderTilgang(regelInput)
    }

    suspend fun harTilgangTilJournalpost(
        ansattIdent: String,
        req: JournalpostTilgangRequest,
        roller: List<Rolle>,
        token: BrukerToken,
        callId: String
    ): Boolean {
        log.info("Sjekker tilgang til journalpost ${req.journalpostId}")
        val journalpostInfo = journalpostOppslag.hentJournalpostInfo(req.journalpostId, callId)

        if (journalpostInfo.gjelderOrganisasjon) {
            log.warn("Journalpost ${journalpostInfo.journalpostId} har orgnr som bruker – tilgang vil vurderes som OK.")
            return true
        }

        val identer = finnIdenterForJournalpost(journalpostInfo)

        val avklaringsbehov =
            if (req.avklaringsbehovKode != null) PostmottakDefinisjon.forKode(req.avklaringsbehovKode!!) else null

        val regelInput = Tilgangsgrunnlag(
            callId = callId,
            ansattIdent = ansattIdent,
            brukerToken = token,
            roller = roller,
            søkerIdenter = identer,
            avklaringsbehovFraBehandlingsflyt = null,
            avklaringsbehovFraPostmottak = avklaringsbehov,
            påkrevdRolle = req.påkrevdRolle ?: emptyList(),
            operasjoner = listOf(req.operasjon),
        )
        return tilgangsvurdering.vurderTilgang(regelInput)[req.operasjon] == true
    }

    suspend fun harTilgangTilArensakerForPerson(
        ansattIdent: String,
        req: ArenasakTilgangRequest,
        token: BrukerToken,
        roller: List<Rolle>,
        callId: String
    ): Boolean {
        val regelInput = Tilgangsgrunnlag(
            callId = callId,
            ansattIdent = ansattIdent,
            brukerToken = token,
            roller = roller,
            søkerIdenter = RelevanteIdenter(listOf(req.personIdent), emptyList()),
            avklaringsbehovFraBehandlingsflyt = null,
            avklaringsbehovFraPostmottak = null,
            påkrevdRolle = req.påkrevdRolle,
            operasjoner = listOf(req.operasjon)
        )

        return tilgangsvurdering.vurderTilgang(regelInput)[req.operasjon] == true
    }

    private suspend fun finnIdenterForJournalpost(journalpost: JournalpostInfo): RelevanteIdenter {
        val saksnummer = journalpost.saksnummer
        log.info("Finner identer på journalpost med saksnummer $saksnummer.")
        if (saksnummer != null) {
            val identer = identOppslag.hentIdenterForSak(saksnummer)
            require(identer.søker.isNotEmpty()) { "Fant ingen søkeridenter for sak $saksnummer" }
            return identer
        } else {
            val søkerIdent = journalpost.søkerIdent
            requireNotNull(søkerIdent) { "Fant ingen ident på søker i journalposten" }
            return RelevanteIdenter(søker = listOf(søkerIdent), barn = emptyList())
        }
    }

    suspend fun harTilgangFraTilgangsmaskin(
        brukerIdenter: List<Personregel>,
        token: BrukerToken,
        callId: String,
    ): Boolean {
        return persontilgang.harTilganger(brukerIdenter, token, callId)
    }

    suspend fun harTilgangTilPerson(
        ansattIdent: String,
        req: PersonTilgangRequest,
        token: BrukerToken,
        roller: List<Rolle>,
        callId: String
    ): Boolean {
        // Bruke intern tilgangssjekk på driftsoperasjoner
        if (req.operasjon in listOf(Operasjon.DRIFTE, Operasjon.DRIFT_LES)) {
            val regelInput = Tilgangsgrunnlag(
                callId = callId,
                ansattIdent = ansattIdent,
                brukerToken = token,
                roller = roller,
                søkerIdenter = RelevanteIdenter(listOf(req.personIdent), emptyList()),
                avklaringsbehovFraBehandlingsflyt = null,
                avklaringsbehovFraPostmottak = null,
                påkrevdRolle = req.påkrevdRolle ?: emptyList(),
                operasjoner = listOf(req.operasjon!!),
            )

            return tilgangsvurdering.vurderTilgang(regelInput)[req.operasjon] == true
        }

        return persontilgang.harTilgangTilPerson(req.personIdent, token, callId)
    }

    suspend fun harTilgangTilTilbakekreving(
        ansattIdent: String,
        req: TilbakekrevingTilgangRequest,
        roller: List<Rolle>,
        token: BrukerToken,
        callId: String,
    ): Boolean {
        log.info("Sjekker tilgang til tilbakekrevingsbehandling ${req.behandlingsreferanse}")
        val identer = identOppslag.hentIdenterForSak(req.saksnummer)

        val regelInput = Tilgangsgrunnlag(
            callId = callId,
            ansattIdent = ansattIdent,
            brukerToken = token,
            roller = roller,
            søkerIdenter = identer,
            avklaringsbehovFraBehandlingsflyt = null,
            avklaringsbehovFraPostmottak = null,
            påkrevdRolle = req.effektivePåkrevdRoller(),
            operasjoner = listOf(req.operasjon)
        )
        return tilgangsvurdering.vurderTilgang(regelInput)[req.operasjon] == true
    }

}