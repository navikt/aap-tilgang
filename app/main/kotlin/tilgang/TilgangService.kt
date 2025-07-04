package tilgang

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import tilgang.integrasjoner.saf.SafGraphqlClient
import tilgang.integrasjoner.saf.SafJournalpost
import no.nav.aap.tilgang.BehandlingTilgangRequest
import no.nav.aap.tilgang.JournalpostTilgangRequest
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.Rolle
import no.nav.aap.tilgang.SakTilgangRequest
import org.slf4j.LoggerFactory
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytClient
import tilgang.integrasjoner.behandlingsflyt.IdenterRespons
import tilgang.integrasjoner.tilgangsmaskin.BrukerOgRegeltype
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinClient
import tilgang.regler.RegelInput
import tilgang.regler.RegelService
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon as PostmottakDefinisjon

class TilgangService(
    private val safClient: SafGraphqlClient,
    private val behandlingsflytClient: BehandlingsflytClient,
    private val regelService: RegelService,
    private val tilgangsmaskinClient: TilgangsmaskinClient
) {
    private val log = LoggerFactory.getLogger(TilgangService::class.java)

    fun harTilgangTilSak(
        ansattIdent: String,
        req: SakTilgangRequest,
        roller: List<Rolle>,
        token: OidcToken,
        callId: String
    ): Boolean {
        log.info("Sjekker tilgang til sak ${req.saksnummer}")
        val identer = behandlingsflytClient.hentIdenterForSak(req.saksnummer)
        val regelInput = RegelInput(
            callId = callId,
            ansattIdent = ansattIdent,
            currentToken = token,
            roller = roller,
            søkerIdenter = identer,
            avklaringsbehovFraBehandlingsflyt = null,
            avklaringsbehovFraPostmottak = null,
            operasjoner = listOf(req.operasjon)
        )
        return regelService.vurderTilgang(regelInput)[req.operasjon] == true
    }

    fun harTilgangTilBehandling(
        ansattIdent: String,
        req: BehandlingTilgangRequest,
        roller: List<Rolle>,
        token: OidcToken,
        callId: String,
    ): Map<Operasjon, Boolean> {
        log.info("Sjekker tilgang til behandling ${req.behandlingsreferanse}")
        val identer = behandlingsflytClient.hentIdenterForBehandling(req.behandlingsreferanse.toString())
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
            operasjoner = (req.operasjonerIKontekst + req.operasjon).toSet().toList()
        )
        return regelService.vurderTilgang(regelInput)
    }

    fun harTilgangTilJournalpost(
        ansattIdent: String,
        req: JournalpostTilgangRequest,
        roller: List<Rolle>,
        token: OidcToken,
        callId: String
    ): Boolean {
        log.info("Sjekker tilgang til journalpost ${req.journalpostId}")
        val journalpostInfo: SafJournalpost = safClient.hentJournalpostInfo(req.journalpostId, callId)
        val identer = finnIdenterForJournalpost(journalpostInfo, token)

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
            operasjoner = listOf(req.operasjon),
        )
        return regelService.vurderTilgang(regelInput)[req.operasjon] == true
    }

    private fun finnIdenterForJournalpost(journalpost: SafJournalpost, token: OidcToken): IdenterRespons {
        val saksnummer = journalpost.sak?.fagsakId
        if(saksnummer != null) {
            val identer = behandlingsflytClient.hentIdenterForSak(saksnummer)
            require(identer.søker.isNotEmpty()) { "Fant ingen søkeridenter for sak $saksnummer" }
            return identer
        } else {
            val søkerIdent = journalpost.bruker?.id
            requireNotNull(søkerIdent)
            return IdenterRespons(søker = listOf(søkerIdent), barn = emptyList())
        }
    }

    fun harTilgangFraTilgangsmaskin(brukerIdenter: List<BrukerOgRegeltype>, token: OidcToken): Boolean {
        return tilgangsmaskinClient.harTilganger(brukerIdenter, token)
    }

    fun harTilgangTilPerson(brukerIdent: String, token: OidcToken): Boolean {
        return tilgangsmaskinClient.harTilgangTilPerson(brukerIdent, token)
    }
}
