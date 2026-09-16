package tilgang

import io.mockk.Called
import io.mockk.InternalPlatformDsl.toStr
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import no.nav.aap.tilgang.Operasjon
import no.nav.aap.tilgang.PersonTilgangRequest
import no.nav.aap.tilgang.RelevanteIdenter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import tilgang.integrasjoner.behandlingsflyt.BehandlingsflytGateway
import tilgang.integrasjoner.saf.SafGraphqlGateway
import tilgang.integrasjoner.tilgangsmaskin.TilgangsmaskinGateway
import tilgang.regler.RegelInput
import tilgang.regler.RegelService
import kotlin.random.Random

class TilgangServiceTest {

    private val safGateway = mockk<SafGraphqlGateway>()
    private val behandlingsflytGateway = mockk<BehandlingsflytGateway>()
    private val regelService = mockk<RegelService>(relaxed = true)
    private val tilgangsmaskinGateway = mockk<TilgangsmaskinGateway>(relaxed = true)

    private val service = TilgangService(
        safGateway = safGateway,
        behandlingsflytGateway = behandlingsflytGateway,
        regelService = regelService,
        tilgangsmaskinGateway = tilgangsmaskinGateway
    )

    @AfterEach
    fun afterEach() {
        clearAllMocks()
    }

    @Nested
    inner class TilgangTilPerson {

        @ParameterizedTest
        @EnumSource(value = Operasjon::class, names = ["DRIFTE"], mode = EnumSource.Mode.EXCLUDE)
        suspend fun `harTilgangTilPerson - skal kalle tilgangsmaskin hvis operasjon ikke er DRIFTE`(operasjon: Operasjon) {
            val personIdent = Random.nextLong().toString()
            val req = PersonTilgangRequest(personIdent, emptyList(), operasjon)

            service.harTilgangTilPerson("ansattIdent", req, mockk(), emptyList(), "callId")

            coVerify {
                regelService wasNot Called

                tilgangsmaskinGateway.harTilgangTilPerson(personIdent, any())
            }
        }

        @Test
        suspend fun `harTilgangTilPerson - skal kun kalle RegelService hvis operasjon er DRIFTE`() {
            val personIdent = Random.nextLong().toString()
            val req = PersonTilgangRequest(personIdent, emptyList(), Operasjon.DRIFTE)

            service.harTilgangTilPerson("ansattIdent", req, mockk(), emptyList(), "callId")

            coVerify {
                regelService.vurderTilgang(any())
                tilgangsmaskinGateway wasNot Called
            }
        }

    }

}