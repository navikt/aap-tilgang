package tilgang.service

import no.nav.aap.komponenter.config.requiredConfigForKey
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import tilgang.integrasjoner.msgraph.IMsGraphGateway
import java.util.*

class AdressebeskyttelseService(private val msGraphClient: IMsGraphGateway) {
    fun hentAdressebeskyttelseRoller(
        currentToken: OidcToken, oboIdent: String
    ): List<AdressebeskyttelseGruppe> {
        return msGraphClient.hentAdGrupper(currentToken, oboIdent).groups
            .filter { gruppe -> gruppe.id in AdressebeskyttelseGruppe.entries.map { it.gruppeId } }
            .map { gruppe -> AdressebeskyttelseGruppe.entries.first { it.gruppeId == gruppe.id } }
    }
}

enum class AdressebeskyttelseGruppe(val gruppeId: UUID) {
    STRENGT_FORTROLIG_ADRESSE(UUID.fromString(requiredConfigForKey("strengt.fortrolig.adresse.ad"))), 
    FORTROLIG_ADRESSE(UUID.fromString(requiredConfigForKey("fortrolig.adresse.ad")))
}