package tilgang.kontroll

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
        input: SakstilgangInput,
        kontekst: Tilgangskontekst,
    ): Boolean {
        log.info("Sjekker tilgang til sak ${input.saksnummer}")
        val identer = input.relevanteIdenter ?: identOppslag
            .hentIdenterForSak(input.saksnummer)

        val grunnlag = Tilgangsgrunnlag(
            kontekst = kontekst,
            søkerIdenter = identer,
            avklaringsbehov = null,
            påkrevdRolle = input.påkrevdRolle,
            operasjoner = listOf(input.operasjon)
        )
        return tilgangsvurdering.vurderTilgang(grunnlag)[input.operasjon] == true
    }

    suspend fun harTilgangTilBehandling(
        input: BehandlingstilgangInput,
        kontekst: Tilgangskontekst,
    ): Map<Operasjon, Boolean> {
        log.info("Sjekker tilgang til behandling ${input.behandlingsreferanse}")
        val identer = input.relevanteIdenter ?: identOppslag
            .hentIdenterForBehandling(input.behandlingsreferanse.toString())

        val avklaringsbehov = input.avklaringsbehovKode?.let {
            Avklaringsbehov(it, AvklaringsbehovType.BEHANDLING)
        }

        val grunnlag = Tilgangsgrunnlag(
            kontekst = kontekst,
            søkerIdenter = identer,
            avklaringsbehov = avklaringsbehov,
            påkrevdRolle = input.påkrevdRolle,
            operasjoner = Operasjon.entries
        )
        return tilgangsvurdering.vurderTilgang(grunnlag)
    }

    suspend fun harTilgangTilJournalpost(
        input: JournalposttilgangInput,
        kontekst: Tilgangskontekst,
    ): Boolean {
        log.info("Sjekker tilgang til journalpost ${input.journalpostId}")
        val journalpostInfo = journalpostOppslag.hentJournalpostInfo(input.journalpostId, kontekst.callId)

        if (journalpostInfo.gjelderOrganisasjon) {
            log.warn("Journalpost ${journalpostInfo.journalpostId} har orgnr som bruker – tilgang vil vurderes som OK.")
            return true
        }

        val identer = finnIdenterForJournalpost(journalpostInfo)

        val avklaringsbehov = input.avklaringsbehovKode?.let {
            Avklaringsbehov(it, AvklaringsbehovType.JOURNALPOST)
        }

        val grunnlag = Tilgangsgrunnlag(
            kontekst = kontekst,
            søkerIdenter = identer,
            avklaringsbehov = avklaringsbehov,
            påkrevdRolle = input.påkrevdRolle,
            operasjoner = listOf(input.operasjon),
        )
        return tilgangsvurdering.vurderTilgang(grunnlag)[input.operasjon] == true
    }

    suspend fun harTilgangTilArenasakerForPerson(
        input: ArenasakstilgangInput,
        kontekst: Tilgangskontekst,
    ): Boolean {
        val grunnlag = Tilgangsgrunnlag(
            kontekst = kontekst,
            søkerIdenter = RelevanteIdenter(listOf(input.personIdent), emptyList()),
            avklaringsbehov = null,
            påkrevdRolle = input.påkrevdRolle,
            operasjoner = listOf(input.operasjon)
        )

        return tilgangsvurdering.vurderTilgang(grunnlag)[input.operasjon] == true
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

    suspend fun harTilgangTilPersoner(
        personregler: List<Personregel>,
        token: BrukerToken,
        callId: String,
    ): Boolean {
        return persontilgang.harTilganger(personregler, token, callId)
    }

    suspend fun harTilgangTilPerson(
        input: PersontilgangInput,
        kontekst: Tilgangskontekst,
    ): Boolean {
        // Bruke intern tilgangssjekk på driftsoperasjoner
        val operasjon = input.operasjon
        if (operasjon == Operasjon.DRIFTE || operasjon == Operasjon.DRIFT_LES) {
            val grunnlag = Tilgangsgrunnlag(
                kontekst = kontekst,
                søkerIdenter = RelevanteIdenter(listOf(input.personIdent), emptyList()),
                avklaringsbehov = null,
                påkrevdRolle = input.påkrevdRolle,
                operasjoner = listOf(operasjon),
            )

            return tilgangsvurdering.vurderTilgang(grunnlag)[operasjon] == true
        }

        return persontilgang.harTilgangTilPerson(input.personIdent, kontekst.brukerToken, kontekst.callId)
    }

    suspend fun harTilgangTilTilbakekreving(
        input: TilbakekrevingstilgangInput,
        kontekst: Tilgangskontekst,
    ): Boolean {
        log.info("Sjekker tilgang til tilbakekrevingsbehandling ${input.behandlingsreferanse}")
        val identer = identOppslag.hentIdenterForSak(input.saksnummer)

        val grunnlag = Tilgangsgrunnlag(
            kontekst = kontekst,
            søkerIdenter = identer,
            avklaringsbehov = null,
            påkrevdRolle = input.påkrevdRolle,
            operasjoner = listOf(input.operasjon)
        )
        return tilgangsvurdering.vurderTilgang(grunnlag)[input.operasjon] == true
    }

}