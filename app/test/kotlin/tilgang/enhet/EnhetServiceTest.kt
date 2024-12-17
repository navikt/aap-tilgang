package tilgang.enhet

import kotlinx.coroutines.runBlocking
import no.nav.aap.komponenter.httpklient.httpclient.tokenprovider.OidcToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tilgang.AzureTokenGen
import tilgang.fakes.TestToken
import tilgang.service.EnhetService
import tilgang.integrasjoner.msgraph.Group
import tilgang.integrasjoner.msgraph.IMsGraphClient
import tilgang.integrasjoner.msgraph.MemberOf
import java.util.*

class EnhetServiceTest {
    @Test
    fun `lister kun opp enhets-roller`() {
        val graphClient = object : IMsGraphClient {
            override fun hentAdGrupper(currentToken: OidcToken, ident: String): MemberOf {
                return MemberOf(
                    groups = listOf(
                        Group(name = "0000-GA-ENHET_12345", id = UUID.randomUUID()),
                        Group(name = "0000-GA-GEO_12345", id = UUID.randomUUID())
                    )
                )
            }
        }
        val service = EnhetService(graphClient)

        runBlocking {
            val token = AzureTokenGen("tilgangazure", "tilgang").generate()
            val res = service.hentEnhetRoller(OidcToken(token), "")
            assertThat(res).isNotEmpty()
            assertThat(res).hasSize(1)
            assertThat(res[0].kode).isEqualTo("12345")
        }
    }
}