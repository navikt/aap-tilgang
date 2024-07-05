package tilgang.regler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tilgang.geo.GeoRolle
import tilgang.geo.GeoType
import tilgang.integrasjoner.pdl.HentGeografiskTilknytningResult
import tilgang.integrasjoner.pdl.PdlGeoType

class GeoRegelTest {
    @Test
    fun `Skal ikke kunne lese saker som er utenfor geografiske rettigher`() {
        val søkersGeografiskeTilknytning = HentGeografiskTilknytningResult(
            PdlGeoType.BYDEL, null, "500101", null
        )
        val input = GeoInput(listOf(GeoRolle(GeoType.KOMMUNE, "0301")), søkersGeografiskeTilknytning)
        assertFalse(GeoRegel.vurder(input))
    }

    @Test
    fun `Skal kunne lese bydel i kommune`() {
        val søkersGeografiskeTilknytning = HentGeografiskTilknytningResult(
            PdlGeoType.BYDEL, null, "030102", null
        )
        val input = GeoInput(listOf(GeoRolle(GeoType.KOMMUNE, "0301")), søkersGeografiskeTilknytning)
        assertTrue(GeoRegel.vurder(input))
    }

}