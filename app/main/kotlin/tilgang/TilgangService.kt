package tilgang

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import tilgang.integrasjoner.saf.SafGraphqlGateway
import tilgang.integrasjoner.saf.SafJournalpost
import no.nav.aap.tilgang.BehandlingTilgangRequest
import no.nav.aap.tilgang.JournalpostTilgangRequest
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.RelevanteIdenter
import no.nav.aap.tilgang.Rolle
import no.nav.aap.tilgang.SakTilgangRequest
import org.slf4j.LoggerFactory
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytGateway
import tilgang.integrasjoner.tilgangsmaskin.BrukerOgRegeltype
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.regler.RegelInput
import tilgang.regler.RegelService
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon as PostmottakDefinisjon

class TilgangService(
    private val safGateway: SafGraphqlGateway,
    private val behandlingsflytGateway: BehandlingsflytGateway,
    private val regelService: RegelService,
    private val tilgangsmaskinGateway: TilgangsmaskinGateway
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
        val identer = req.relevanteIdenter ?: behandlingsflytGateway
            .hentIdenterForSak(req.saksnummer)
        
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
        val identer = req.relevanteIdenter ?: behandlingsflytGateway
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
        val journalpostInfo: SafJournalpost = safGateway.hentJournalpostInfo(req.journalpostId, callId)
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
            operasjoner = listOf(req.operasjon),
        )
        return regelService.vurderTilgang(regelInput)[req.operasjon] == true
    }

    private fun finnIdenterForJournalpost(journalpost: SafJournalpost): RelevanteIdenter {
        val saksnummer = finnKelvinSaksnummerForJournalpost(journalpost)
        log.info("Finner identer på journalpost med saksnummer $saksnummer.")
        if (saksnummer != null) {
            val identer = behandlingsflytGateway.hentIdenterForSak(saksnummer)
            require(identer.søker.isNotEmpty()) { "Fant ingen søkeridenter for sak $saksnummer" }
            return identer
        } else {
            val søkerIdent = journalpost.bruker?.id
            requireNotNull(søkerIdent)
            return RelevanteIdenter(søker = listOf(søkerIdent), barn = emptyList())
        }
    }

    private fun finnKelvinSaksnummerForJournalpost(journalpost: SafJournalpost): String? {
        return when (journalpost.sak?.fagsaksystem) {
            "KELVIN" -> journalpost.sak.fagsakId
            else -> null
        }
    }

    fun harTilgangFraTilgangsmaskin(brukerIdenter: List<BrukerOgRegeltype>, token: OidcToken): Boolean {
        return tilgangsmaskinGateway.harTilganger(brukerIdenter, token)
    }

    fun harTilgangTilPerson(brukerIdent: String, token: OidcToken): Boolean {
        return tilgangsmaskinGateway.harTilgangTilPerson(brukerIdent, token)
    }
}
