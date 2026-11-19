package tilgang

import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.RelevanteIdenter
import org.slf4j.LoggerFactory

class TilgangService(
    private val journalpostOppslag: JournalpostOppslag,
    private val identOppslag: IdentOppslag,
    private val tilgangsvurdering: Tilgangsvurdering,
    private val persontilgang: Persontilgang
) {
    private val log = LoggerFactory.getLogger(TilgangService::class.java)

    suspend fun harTilgangTilSak(
        req: SakstilgangInput,
        kontekst: Tilgangskontekst,
    ): Boolean {
        log.info("Sjekker tilgang til sak ${req.saksnummer}")
        val identer = req.relevanteIdenter ?: identOppslag
            .hentIdenterForSak(req.saksnummer)

        val regelInput = Tilgangsgrunnlag(
            kontekst = kontekst,
            søkerIdenter = identer,
            avklaringsbehov = null,
            påkrevdRolle = req.påkrevdRolle,
            operasjoner = listOf(req.operasjon)
        )
        return tilgangsvurdering.vurderTilgang(regelInput)[req.operasjon] == true
    }

    suspend fun harTilgangTilBehandling(
        req: BehandlingstilgangInput,
        kontekst: Tilgangskontekst,
    ): Map<Operasjon, Boolean> {
        log.info("Sjekker tilgang til behandling ${req.behandlingsreferanse}")
        val identer = req.relevanteIdenter ?: identOppslag
            .hentIdenterForBehandling(req.behandlingsreferanse.toString())

        val avklaringsbehov = req.avklaringsbehovKode?.let {
            Avklaringsbehov(it, AvklaringsbehovType.BEHANDLING)
        }

        val regelInput = Tilgangsgrunnlag(
            kontekst = kontekst,
            søkerIdenter = identer,
            avklaringsbehov = avklaringsbehov,
            påkrevdRolle = req.påkrevdRolle,
            operasjoner = Operasjon.entries
        )
        return tilgangsvurdering.vurderTilgang(regelInput)
    }

    suspend fun harTilgangTilJournalpost(
        req: JournalposttilgangInput,
        kontekst: Tilgangskontekst,
    ): Boolean {
        log.info("Sjekker tilgang til journalpost ${req.journalpostId}")
        val journalpostInfo = journalpostOppslag.hentJournalpostInfo(req.journalpostId, kontekst.callId)

        if (journalpostInfo.gjelderOrganisasjon) {
            log.warn("Journalpost ${journalpostInfo.journalpostId} har orgnr som bruker – tilgang vil vurderes som OK.")
            return true
        }

        val identer = finnIdenterForJournalpost(journalpostInfo)

        val avklaringsbehov = req.avklaringsbehovKode?.let {
            Avklaringsbehov(it, AvklaringsbehovType.JOURNALPOST)
        }

        val regelInput = Tilgangsgrunnlag(
            kontekst = kontekst,
            søkerIdenter = identer,
            avklaringsbehov = avklaringsbehov,
            påkrevdRolle = req.påkrevdRolle,
            operasjoner = listOf(req.operasjon),
        )
        return tilgangsvurdering.vurderTilgang(regelInput)[req.operasjon] == true
    }

    suspend fun harTilgangTilArensakerForPerson(
        req: ArenasakstilgangInput,
        kontekst: Tilgangskontekst,
    ): Boolean {
        val regelInput = Tilgangsgrunnlag(
            kontekst = kontekst,
            søkerIdenter = RelevanteIdenter(listOf(req.personIdent), emptyList()),
            avklaringsbehov = null,
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
        req: PersontilgangInput,
        kontekst: Tilgangskontekst,
    ): Boolean {
        // Bruke intern tilgangssjekk på driftsoperasjoner
        if (req.operasjon in listOf(Operasjon.DRIFTE, Operasjon.DRIFT_LES)) {
            val regelInput = Tilgangsgrunnlag(
                kontekst = kontekst,
                søkerIdenter = RelevanteIdenter(listOf(req.personIdent), emptyList()),
                avklaringsbehov = null,
                påkrevdRolle = req.påkrevdRolle,
                operasjoner = listOf(req.operasjon!!),
            )

            return tilgangsvurdering.vurderTilgang(regelInput)[req.operasjon] == true
        }

        return persontilgang.harTilgangTilPerson(req.personIdent, kontekst.brukerToken, kontekst.callId)
    }

    suspend fun harTilgangTilTilbakekreving(
        req: TilbakekrevingstilgangInput,
        kontekst: Tilgangskontekst,
    ): Boolean {
        log.info("Sjekker tilgang til tilbakekrevingsbehandling ${req.behandlingsreferanse}")
        val identer = identOppslag.hentIdenterForSak(req.saksnummer)

        val regelInput = Tilgangsgrunnlag(
            kontekst = kontekst,
            søkerIdenter = identer,
            avklaringsbehov = null,
            påkrevdRolle = req.påkrevdRolle,
            operasjoner = listOf(req.operasjon)
        )
        return tilgangsvurdering.vurderTilgang(regelInput)[req.operasjon] == true
    }

}