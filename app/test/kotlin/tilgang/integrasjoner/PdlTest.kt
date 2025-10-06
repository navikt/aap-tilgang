package tilgang.integrasjoner

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import tilgang.fakes.Fakes
import tilgang.integrasjoner.pdl.PdlGraphQLClient

class PdlTest {
    companion object {
        private val fakes = Fakes()

        @AfterAll
        @JvmStatic
        fun afterall() {
            fakes.close()
        }
    }

    @Test
    fun `Kan parse hentPersonBolk`() {
        val test =
            PdlGraphQLClient(fakes.redis, fakes.prometheues).hentPersonBolk(listOf("1234"), "test")

        assertThat(test?.size).isEqualTo(1)
    }

}