package tilgang.service

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import tilgang.integrasjoner.msgraph.IMsGraphGateway
import java.util.*

class SkjermingService(private val msGraphGateway: IMsGraphGateway) {
    private val skjermedePersonerGruppeId = UUID.fromString(requiredConfigForKey("skjermede.personer.ad"))

    fun harSkjermedePersonerRolle(
        currentToken: OidcToken, oboIdent: String
    ): Boolean {
        return msGraphGateway.hentAdGrupper(currentToken, oboIdent).groups.any { it.id == skjermedePersonerGruppeId }
    }
}