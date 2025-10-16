package tilgang.regler

import no.nav.aap.komponenter.miljo.Miljø
import no.nav.aap.komponenter.miljo.MiljøKode
import org.slf4j.LoggerFactory
import tilgang.service.GeoRolle
import tilgang.service.GeoService
import tilgang.service.GeoType
import tilgang.integrasjoner.pdl.HentGeografiskTilknytningResult
import tilgang.integrasjoner.pdl.IPdlGraphQLGateway
import tilgang.integrasjoner.pdl.PdlGeoType

data object GeoRegel : Regel<GeoInput> {
    private val log = LoggerFactory.getLogger(GeoRegel::class.java)
    override fun vurder(input: GeoInput): Boolean {
        val (geoRoller, søkersGeografiskeTilknytning) = input

        if (søkersGeografiskeTilknytning == null) {
            if (Miljø.er() == MiljøKode.PROD) {
                log.warn("Fant ikke geografisk tilknytning for ident i PDL - returnerer false")
            } else {
                log.info("Fant ikke geografisk tilknytning for ident i PDL - returnerer false")
            }
            return false
        }

        val harNasjonalTilgang = geoRoller.any { it.geoType === GeoType.NASJONAL }

        return when (søkersGeografiskeTilknytning.gtType) {
            PdlGeoType.KOMMUNE -> harNasjonalTilgang
                    || søkersGeografiskeTilknytning.gtKommune in geoRoller.filter { it.geoType === GeoType.KOMMUNE }
                .map { it.kode }

            PdlGeoType.BYDEL -> harNasjonalTilgang
                    || harRettigheterTilBydel(requireNotNull(søkersGeografiskeTilknytning.gtBydel), geoRoller)

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

class GeoInputGenerator(
    private val geoService: GeoService,
    private val pdlClient: IPdlGraphQLGateway
) :
    InputGenerator<GeoInput> {
    override fun generer(input: RegelInput): GeoInput {
        val geoRoller = geoService.hentGeoRoller(input.currentToken, input.ansattIdent)
        val søkersGeografiskeTilknytning = pdlClient.hentGeografiskTilknytning(
            input.søkerIdenter.søker.first(),
            input.callId
        )
        return GeoInput(geoRoller, søkersGeografiskeTilknytning, input.ansattIdent)
    }
}

data class GeoInput(
    val geoRoller: List<GeoRolle>,
    val søkersGeografiskTilknytning: HentGeografiskTilknytningResult?,
    val ident: String
)