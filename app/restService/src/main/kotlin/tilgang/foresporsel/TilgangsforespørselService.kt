package tilgang.foresporsel

import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle
import tilgang.grunnlag.TilgangsgrunnlagService
import tilgang.kontroll.ArenasakstilgangInput
import tilgang.kontroll.Avklaringsbehov
import tilgang.kontroll.AvklaringsbehovType
import tilgang.kontroll.BehandlingstilgangInput
import tilgang.kontroll.BrukerToken
import tilgang.kontroll.JournalposttilgangInput
import tilgang.kontroll.Personregel
import tilgang.kontroll.PersontilgangInput
import tilgang.kontroll.SakstilgangInput
import tilgang.kontroll.TilbakekrevingstilgangInput
import tilgang.kontroll.TilgangService
import tilgang.kontroll.Tilgangsgrunnlag
import tilgang.kontroll.Tilgangskontekst
import tilgang.logging.ILogger

class TilgangsforespørselService(
    private val grunnlagService: TilgangsgrunnlagService,
    private val tilgangService: TilgangService,
    private val persontilgang: Persontilgang,
    private val log: ILogger,
) {
    suspend fun harTilgangTilSak(
        input: SakstilgangInput,
        kontekst: Tilgangskontekst,
    ): Boolean {
        log.info("Sjekker tilgang til sak ${input.saksnummer}")
        val identer = grunnlagService.hentIdenterForSak(input.saksnummer, input.relevanteIdenter)

        return vurderOperasjon(
            kontekst = kontekst,
            søkerIdenter = identer,
            påkrevdRolle = input.påkrevdRolle,
            operasjon = input.operasjon,
        )
    }

    suspend fun harTilgangTilBehandling(
        input: BehandlingstilgangInput,
        kontekst: Tilgangskontekst,
    ): Map<Operasjon, Boolean> {
        log.info("Sjekker tilgang til behandling ${input.behandlingsreferanse}")
        val identer = grunnlagService.hentIdenterForBehandling(
            input.behandlingsreferanse, input.relevanteIdenter
        )

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
        return tilgangService.vurderTilgang(grunnlag)
    }

    suspend fun harTilgangTilJournalpost(
        input: JournalposttilgangInput,
        kontekst: Tilgangskontekst,
    ): Boolean {
        log.info("Sjekker tilgang til journalpost ${input.journalpostId}")
        val journalpostInfo = grunnlagService.hentJournalpostInfo(input.journalpostId, kontekst.callId)

        if (journalpostInfo.gjelderOrganisasjon) {
            log.warn("Journalpost ${journalpostInfo.journalpostId} har orgnr som bruker – tilgang vil vurderes som OK.")
            return true
        }

        val identer = grunnlagService.finnIdenterForJournalpost(journalpostInfo)

        val avklaringsbehov = input.avklaringsbehovKode?.let {
            Avklaringsbehov(it, AvklaringsbehovType.JOURNALPOST)
        }

        return vurderOperasjon(
            kontekst = kontekst,
            søkerIdenter = identer,
            avklaringsbehov = avklaringsbehov,
            påkrevdRolle = input.påkrevdRolle,
            operasjon = input.operasjon,
        )
    }

    suspend fun harTilgangTilArenasakerForPerson(
        input: ArenasakstilgangInput,
        kontekst: Tilgangskontekst,
    ): Boolean {
        return vurderOperasjon(
            kontekst = kontekst,
            søkerIdenter = RelevanteIdenter(listOf(input.personIdent), emptyList()),
            påkrevdRolle = input.påkrevdRolle,
            operasjon = input.operasjon,
        )
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
            return vurderOperasjon(
                kontekst = kontekst,
                søkerIdenter = RelevanteIdenter(listOf(input.personIdent), emptyList()),
                påkrevdRolle = input.påkrevdRolle,
                operasjon = operasjon,
            )
        }

        return persontilgang.harTilgangTilPerson(input.personIdent, kontekst.brukerToken, kontekst.callId)
    }

    suspend fun harTilgangTilTilbakekreving(
        input: TilbakekrevingstilgangInput,
        kontekst: Tilgangskontekst,
    ): Boolean {
        log.info("Sjekker tilgang til tilbakekrevingsbehandling ${input.behandlingsreferanse}")
        val identer = grunnlagService.hentIdenterForSak(input.saksnummer)

        return vurderOperasjon(
            kontekst = kontekst,
            søkerIdenter = identer,
            påkrevdRolle = input.påkrevdRolle,
            operasjon = input.operasjon,
        )
    }

    private suspend fun vurderOperasjon(
        kontekst: Tilgangskontekst,
        søkerIdenter: RelevanteIdenter,
        påkrevdRolle: List<Rolle>,
        operasjon: Operasjon,
        avklaringsbehov: Avklaringsbehov? = null,
    ): Boolean = tilgangService.vurderTilgang(
        Tilgangsgrunnlag(
            kontekst = kontekst,
            søkerIdenter = søkerIdenter,
            avklaringsbehov = avklaringsbehov,
            påkrevdRolle = påkrevdRolle,
            operasjoner = listOf(operasjon),
        )
    )[operasjon] == true
}