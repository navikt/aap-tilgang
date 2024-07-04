package tilgang.regler

import tilgang.geo.GeoRolle
import tilgang.geo.GeoService
import tilgang.geo.GeoType
import tilgang.integrasjoner.pdl.HentGeografiskTilknytningResult
import tilgang.integrasjoner.pdl.PdlGeoType
import tilgang.integrasjoner.pdl.PdlGraphQLClient

data object GeoRegel : Regel<GeoInput> {

    override fun vurder(input: GeoInput): Boolean {
        val (geoRoller, søkersGeografiskeTilknytning) = input

        if (geoRoller.any { it.geoType === GeoType.NASJONAL }) {
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
}

class GeoInputGenerator(private val geoService: GeoService, private val pdlClient: PdlGraphQLClient) :
    InputGenerator<GeoInput> {
    override suspend fun generer(input: RegelInput): GeoInput {
        val geoRoller = geoService.hentGeoRoller(input.currentToken)
        val søkersGeografiskeTilknytning = requireNotNull(
            pdlClient.hentGeografiskTilknytning(
                input.identer.first(),
                input.callId
            )
        )
        return GeoInput(geoRoller, søkersGeografiskeTilknytning)
    }
}

data class GeoInput(
    val geoRoller: List<GeoRolle>,
    val søkersGeografiskTilknytningResult: HentGeografiskTilknytningResult
)