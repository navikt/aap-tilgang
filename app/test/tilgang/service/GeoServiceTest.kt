package tilgang.service

import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tilgang.AzureTokenGen
import tilgang.integrasjoner.msgraph.Group
import tilgang.integrasjoner.msgraph.IMsGraphClient
import tilgang.integrasjoner.msgraph.MemberOf
import java.util.*

class GeoServiceTest {
    @Test
    fun `kan parse roller fra Azure`() {
        val geoService = GeoService(object : IMsGraphClient {
            override fun hentAdGrupper(currentToken: OidcToken, ident: String): MemberOf {
                return MemberOf(
                    listOf(
                        Group(UUID.randomUUID(), "0000-GA-GEO_NASJONAL"),
                        Group(UUID.randomUUID(), "0000-GA-GEO_UTLAND"),
                        Group(UUID.randomUUID(), "0000-GA-GEO_UDEFINERT")
                    )
                )
            }
        })

        val token = AzureTokenGen("tilgangazure", "tilgang").generate()

        assertThat(geoService.hentGeoRoller(OidcToken(token), "xxx")).containsExactlyInAnyOrder(
            GeoRolle(
                GeoType.NASJONAL,
                kode = null
            ),
            GeoRolle(
                GeoType.UTLAND,
                kode = null
            ),
            GeoRolle(
                GeoType.UDEFINERT,
                kode = null
            )
        )
    }
}