package tilgang

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
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
import tilgang.integrasjoner.saf.BrukerIdType
import tilgang.integrasjoner.saf.SafJournalpost
import tilgang.integrasjoner.tilgangsmaskin.BrukerOgRegeltype
import tilgang.regler.RegelInput

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
        token: OidcToken,
        callId: String
    ): Boolean {
        log.info("Sjekker tilgang til sak ${req.saksnummer}")
        val identer = req.relevanteIdenter ?: identOppslag
            .hentIdenterForSak(req.saksnummer)

        val regelInput = RegelInput(
            callId = callId,
            ansattIdent = ansattIdent,
            currentToken = token,
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
        token: OidcToken,
        callId: String,
    ): Map<Operasjon, Boolean> {
        log.info("Sjekker tilgang til behandling ${req.behandlingsreferanse}")
        val identer = req.relevanteIdenter ?: identOppslag
            .hentIdenterForBehandling(req.behandlingsreferanse.toString())

        val avklaringsbehov =
            if (req.avklaringsbehovKode != null) Definisjon.forKode(req.avklaringsbehovKode!!) else null

        val regelInput = RegelInput(
            callId = callId,
            ansattIdent = ansattIdent,
            currentToken = token,
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
        token: OidcToken,
        callId: String
    ): Boolean {
        log.info("Sjekker tilgang til journalpost ${req.journalpostId}")
        val journalpostInfo: SafJournalpost = journalpostOppslag.hentJournalpostInfo(req.journalpostId, callId)

        if (journalpostInfo.bruker?.type == BrukerIdType.ORGNR) {
            log.warn("Journalpost ${journalpostInfo.journalpostId} har orgnr som bruker – tilgang vil vurderes som OK.")
            return true
        }

        val identer = finnIdenterForJournalpost(journalpostInfo)

        val avklaringsbehov =
            if (req.avklaringsbehovKode != null) PostmottakDefinisjon.forKode(req.avklaringsbehovKode!!) else null

        val regelInput = RegelInput(
            callId = callId,
            ansattIdent = ansattIdent,
            currentToken = token,
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
        token: OidcToken,
        roller: List<Rolle>,
        callId: String
    ): Boolean {
        val regelInput = RegelInput(
            callId = callId,
            ansattIdent = ansattIdent,
            currentToken = token,
            roller = roller,
            søkerIdenter = RelevanteIdenter(listOf(req.personIdent), emptyList()),
            avklaringsbehovFraBehandlingsflyt = null,
            avklaringsbehovFraPostmottak = null,
            påkrevdRolle = req.påkrevdRolle,
            operasjoner = listOf(req.operasjon)
        )

        return tilgangsvurdering.vurderTilgang(regelInput)[req.operasjon] == true
    }

    private suspend fun finnIdenterForJournalpost(journalpost: SafJournalpost): RelevanteIdenter {
        val saksnummer = finnKelvinSaksnummerForJournalpost(journalpost)
        log.info("Finner identer på journalpost med saksnummer $saksnummer.")
        if (saksnummer != null) {
            val identer = identOppslag.hentIdenterForSak(saksnummer)
            require(identer.søker.isNotEmpty()) { "Fant ingen søkeridenter for sak $saksnummer" }
            return identer
        } else {
            val søkerIdent = journalpost.bruker?.id
            requireNotNull(søkerIdent) { "Fant ingen ident på søker i journalposten" }
            return RelevanteIdenter(søker = listOf(søkerIdent), barn = emptyList())
        }
    }

    private fun finnKelvinSaksnummerForJournalpost(journalpost: SafJournalpost): String? {
        return when (journalpost.sak?.fagsaksystem) {
            "KELVIN" -> journalpost.sak.fagsakId
            else -> null
        }
    }

    suspend fun harTilgangFraTilgangsmaskin(
        brukerIdenter: List<BrukerOgRegeltype>,
        token: OidcToken,
        callId: String,
    ): Boolean {
        return persontilgang.harTilganger(brukerIdenter, token, callId)
    }

    suspend fun harTilgangTilPerson(
        ansattIdent: String,
        req: PersonTilgangRequest,
        token: OidcToken,
        roller: List<Rolle>,
        callId: String
    ): Boolean {
        // Bruke intern tilgangssjekk på driftsoperasjoner
        if (req.operasjon in listOf(Operasjon.DRIFTE, Operasjon.DRIFT_LES)) {
            val regelInput = RegelInput(
                callId = callId,
                ansattIdent = ansattIdent,
                currentToken = token,
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
        token: OidcToken,
        callId: String,
    ): Boolean {
        log.info("Sjekker tilgang til tilbakekrevingsbehandling ${req.behandlingsreferanse}")
        val identer = identOppslag.hentIdenterForSak(req.saksnummer)

        val regelInput = RegelInput(
            callId = callId,
            ansattIdent = ansattIdent,
            currentToken = token,
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