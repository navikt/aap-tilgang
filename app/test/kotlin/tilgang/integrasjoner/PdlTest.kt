package tilgang.integrasjoner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tilgang.fakes.Fakes
import tilgang.fakes.WithFakes
import tilgang.integrasjoner.pdl.PdlGraphQLGateway

@WithFakes
class PdlTest {

    @Test
    fun `Kan parse hentPersonBolk`() {
        val test =
            PdlGraphQLGateway(Fakes.redis, Fakes.prometheus).hentPersonBolk(listOf("1234"), "test")

        assertThat(test?.size).isEqualTo(1)
    }
}
