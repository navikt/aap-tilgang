package tilgang.regler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tilgang.geo.GeoRolle
import tilgang.geo.GeoType
import tilgang.integrasjoner.pdl.HentGeografiskTilknytningResult
import tilgang.integrasjoner.pdl.PdlGeoType

class GeoRegelTest {
    @ParameterizedTest(name = "Skal returnere {2} for rolle {1} og geografisk tilknytning {0}")
    @MethodSource("testData")
    fun geoTest(
        geografiskTilknytning: HentGeografiskTilknytningResult,
        rolle: GeoRolle,
        forventet: Boolean
    ) {
        assertEquals(forventet, GeoRegel.vurder(GeoInput(listOf(rolle), geografiskTilknytning, "")))
    }

    companion object {
        @JvmStatic
        fun testData(): List<Arguments> {
            val osloBydelTilknytning = HentGeografiskTilknytningResult(
                PdlGeoType.BYDEL, null, "030102", null
            )
            val osloKommuneTilknytning = HentGeografiskTilknytningResult(
                PdlGeoType.KOMMUNE, "0301", null, null
            )
            val nordreFolloTilknytning = HentGeografiskTilknytningResult(
                PdlGeoType.KOMMUNE, "3207", null, null
            )
            val utlandTilknytning = HentGeografiskTilknytningResult(PdlGeoType.UTLAND, null, null, null)
            val udefinertTilknytning = HentGeografiskTilknytningResult(PdlGeoType.UDEFINERT, null, null, null)

            val osloKommuneRolle = GeoRolle(GeoType.KOMMUNE, "0301")
            val utlandRolle = GeoRolle(GeoType.UTLAND, null)
            val nasjonalRolle = GeoRolle(GeoType.NASJONAL, null)
            val udefinertRolle = GeoRolle(GeoType.UDEFINERT, null)
            return listOf(
                Arguments.of(osloBydelTilknytning, osloKommuneRolle, true),
                Arguments.of(osloKommuneTilknytning, osloKommuneRolle, true),
                Arguments.of(nordreFolloTilknytning, osloKommuneRolle, false),
                Arguments.of(utlandTilknytning, osloKommuneRolle, false),
                Arguments.of(udefinertTilknytning, osloKommuneRolle, false),
                Arguments.of(osloKommuneTilknytning, utlandRolle, false),
                Arguments.of(utlandTilknytning, utlandRolle, true),
                Arguments.of(udefinertTilknytning, utlandRolle, false),
                Arguments.of(osloBydelTilknytning, nasjonalRolle, true),
                Arguments.of(osloKommuneTilknytning, nasjonalRolle, true),
                Arguments.of(utlandTilknytning, nasjonalRolle, false),
                Arguments.of(udefinertTilknytning, nasjonalRolle, false),
                Arguments.of(udefinertTilknytning, udefinertRolle, true),
                Arguments.of(osloBydelTilknytning, udefinertRolle, false),
                Arguments.of(utlandTilknytning, udefinertRolle, false),
            )
        }
    }
}