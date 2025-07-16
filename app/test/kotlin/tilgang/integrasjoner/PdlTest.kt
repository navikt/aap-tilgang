package tilgang.integrasjoner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tilgang.fakes.WithFakes
import tilgang.fakes.WithFakes.Companion.fakes
import tilgang.integrasjoner.pdl.PdlGraphQLClient

class PdlTest : WithFakes {
    @Test
    fun `Kan parse hentPersonBolk`() {
        val test =
            PdlGraphQLClient(fakes.redis, fakes.prometheues).hentPersonBolk(listOf("1234"), "test")

        assertThat(test?.size).isEqualTo(1)
    }

}