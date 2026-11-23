package tilgang.foresporsel

import tilgang.kontroll.Operasjon
import tilgang.kontroll.RelevanteIdenter
import tilgang.kontroll.Rolle
import org.slf4j.Logger
import tilgang.grunnlag.AvklaringsbehovType
import tilgang.grunnlag.TilgangsgrunnlagService
import tilgang.kontroll.ArenasakstilgangInput
import tilgang.kontroll.BehandlingstilgangInput
import tilgang.kontroll.JournalposttilgangInput
import tilgang.kontroll.PersontilgangInput
import tilgang.kontroll.SakstilgangInput
import tilgang.kontroll.TilbakekrevingstilgangInput
import tilgang.kontroll.TilgangService
import tilgang.kontroll.Tilgangsgrunnlag
import tilgang.kontroll.Tilgangskontekst

class TilgangsforespørselService(
    private val grunnlagService: TilgangsgrunnlagService,
    private val tilgangService: TilgangService,
    private val persontilgang: Persontilgang,
    private val log: Logger,
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

        val avklaringsbehovRoller = grunnlagService.finnAvklaringsbehovRoller(
            input.avklaringsbehovKode, AvklaringsbehovType.BEHANDLING
        )

        val grunnlag = Tilgangsgrunnlag(
            kontekst = kontekst,
            søkerIdenter = identer,
            avklaringsbehovRoller = avklaringsbehovRoller,
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

        val avklaringsbehovRoller = grunnlagService.finnAvklaringsbehovRoller(
            input.avklaringsbehovKode, AvklaringsbehovType.JOURNALPOST
        )

        return vurderOperasjon(
            kontekst = kontekst,
            søkerIdenter = identer,
            avklaringsbehovRoller = avklaringsbehovRoller,
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
        avklaringsbehovRoller: List<List<Rolle>> = emptyList(),
    ): Boolean = tilgangService.vurderTilgang(
        Tilgangsgrunnlag(
            kontekst = kontekst,
            søkerIdenter = søkerIdenter,
            avklaringsbehovRoller = avklaringsbehovRoller,
            påkrevdRolle = påkrevdRolle,
            operasjoner = listOf(operasjon),
        )
    )[operasjon] == true
}