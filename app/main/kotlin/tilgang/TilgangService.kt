package tilgang

import no.nav.aap.behandlingsflyt.kontrakt.avklaringsbehov.Definisjon
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import no.nav.aap.postmottak.saf.graphql.SafGraphqlClient
import no.nav.aap.postmottak.saf.graphql.SafJournalpost
import org.slf4j.LoggerFactory
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytClient
import tilgang.integrasjoner.behandlingsflyt.IdenterRespons
import tilgang.regler.RegelInput
import tilgang.regler.RegelService
import no.nav.aap.postmottak.kontrakt.avklaringsbehov.Definisjon as PostmottakDefinisjon

class TilgangService(
    private val safClient: SafGraphqlClient,
    private val behandlingsflytClient: BehandlingsflytClient,
    private val regelService: RegelService
) {
    private val log = LoggerFactory.getLogger(TilgangService::class.java)

    fun harTilgangTilSak(
        ansattIdent: String,
        req: SakTilgangRequest,
        roller: List<Rolle>,
        token: OidcToken,
        callId: String
    ): Boolean {
        val identer = behandlingsflytClient.hentIdenterForSak(req.saksnummer)
        val regelInput = RegelInput(
            callId = callId,
            ansattIdent = ansattIdent,
            currentToken = token,
            roller = roller,
            søkerIdenter = identer,
            avklaringsbehovFraBehandlingsflyt = null,
            avklaringsbehovFraPostmottak = null,
            operasjon = req.operasjon
        )
        return regelService.vurderTilgang(regelInput)
    }

    fun harTilgangTilBehandling(
        ansattIdent: String,
        req: BehandlingTilgangRequest,
        roller: List<Rolle>,
        token: OidcToken,
        callId: String
    ): Boolean {
        val identer = behandlingsflytClient.hentIdenterForBehandling(req.behandlingsreferanse)
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
            operasjon = req.operasjon
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
            operasjon = req.operasjon
        )
        return regelService.vurderTilgang(regelInput)
    }

    private fun finnIdenterForJournalpost(journalpost: SafJournalpost, token: OidcToken): IdenterRespons {
        val saksnummer = journalpost.sak?.fagsakId
        if(saksnummer != null) {
            return behandlingsflytClient.hentIdenterForSak(saksnummer)
        } else {
            val søkerIdent = journalpost.bruker?.id
            requireNotNull(søkerIdent)
            return IdenterRespons(søker = listOf(søkerIdent), barn = emptyList())
        }
    }
}
