package tilgang.service

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import tilgang.integrasjoner.msgraph.IMsGraphClient
import java.util.*

class SkjermingService(private val msGraphClient: IMsGraphClient) {
    fun harSkjermedePersonerRolle(
        currentToken: OidcToken, oboIdent: String
    ): Boolean {
        val gruppeId = UUID.fromString(requiredConfigForKey("skjermede.personer.ad"))
        return msGraphClient.hentAdGrupper(currentToken, oboIdent).groups.any {it.id == gruppeId}
    }
}