package tilgang.service

import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import tilgang.integrasjoner.msgraph.IMsGraphGateway

class GeoService(private val msGraphGateway: IMsGraphGateway) {

    suspend fun hentGeoRoller(currentToken: OidcToken, ident: String): List<GeoRolle> {
        return msGraphGateway.hentAdGrupper(currentToken, ident).groups
            .mapNotNull { it.name }
            .filter { it.startsWith(GEO_GROUP_PREFIX) }
            .map { parseGeoRolle(it) }
    }

    private fun parseGeoRolle(rolleNavn: String): GeoRolle {
        val kode = rolleNavn.removePrefix("${GEO_GROUP_PREFIX}_")
        return when (kode) {
            "UTLAND" -> GeoRolle(GeoType.UTLAND, null)
            "UDEFINERT" -> GeoRolle(GeoType.UDEFINERT, null)
            "NASJONAL" -> GeoRolle(GeoType.NASJONAL, null)
            else -> parseKode(kode)
        }
    }

    private fun parseKode(kode: String): GeoRolle {
        return when (kode.length) {
            4 -> GeoRolle(GeoType.KOMMUNE, kode)
            6 -> GeoRolle(GeoType.BYDEL, kode)
            else -> error("Klarte ikke parse geokode $kode")
        }
    }

    companion object {
        const val GEO_GROUP_PREFIX = "0000-GA-GEO"
    }

}

data class GeoRolle(val geoType: GeoType, val kode: String?)
enum class GeoType {
    NASJONAL,
    UDEFINERT,
    UTLAND,
    KOMMUNE,
    BYDEL
}