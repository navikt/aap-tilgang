package tilgang

import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import tilgang.integrasjoner.msgraph.Group
import tilgang.integrasjoner.msgraph.IMsGraphGateway
import tilgang.integrasjoner.msgraph.MemberOf
import tilgang.service.GeoRolle
import tilgang.service.GeoService
import tilgang.service.GeoType
import java.util.UUID

class GeoServiceTest {
    @Test
    fun `kan parse roller fra Azure`() {
        val geoService = GeoService(object : IMsGraphGateway {
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

        Assertions.assertThat(geoService.hentGeoRoller(OidcToken(token), "xxx")).containsExactlyInAnyOrder(
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