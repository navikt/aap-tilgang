package tilgang.regler

import tilgang.geo.GeoRolle
import tilgang.geo.GeoType
import tilgang.integrasjoner.pdl.HentGeografiskTilknytningResult
import tilgang.integrasjoner.pdl.PdlGeoType

fun sjekkGeo(
    geoRoller: List<GeoRolle>,
    søkersGeografiskeTilknytning: HentGeografiskTilknytningResult
): Boolean {
    if (geoRoller.any { it.geoType == GeoType.NASJONAL }) {
        return true
    }
    return when (søkersGeografiskeTilknytning.gtType) {
        PdlGeoType.KOMMUNE -> søkersGeografiskeTilknytning.gtKommune in geoRoller.filter { it.geoType === GeoType.KOMMUNE }
            .map { it.kode }

        PdlGeoType.BYDEL -> harRettigheterTilBydel(requireNotNull(søkersGeografiskeTilknytning.gtBydel), geoRoller)
        PdlGeoType.UTLAND -> geoRoller.any { it.geoType == GeoType.UTLAND }
        PdlGeoType.UDEFINERT -> geoRoller.any { it.geoType == GeoType.UDEFINERT }
    }
}

private fun harRettigheterTilBydel(bydel: String, geoRoller: List<GeoRolle>): Boolean {
    val kommune = bydel.substring(0, 4)

    return listOf(bydel, kommune).any {
        it in geoRoller
            .filter { it.geoType === GeoType.BYDEL || it.geoType === GeoType.KOMMUNE }
            .map { it.kode }
    }
}