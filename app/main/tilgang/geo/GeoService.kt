package tilgang.geo

import tilgang.integrasjoner.msgraph.MsGraphClient

class GeoService(private val msGraphClient: MsGraphClient) {

    suspend fun hentGeoRoller(currentToken: String): List<String> {
        return msGraphClient.hentAdGrupper(currentToken).groups
            .filter { it.name.startsWith(GEO_GROUP_PREFIX) }
            .map { it.name }
    }

    companion object {
        const val GEO_GROUP_PREFIX = "0000-GA-GEO"
        // TODO: Navn ikke avklart
        const val NASJONAL = "NASJONAL"
    }
}