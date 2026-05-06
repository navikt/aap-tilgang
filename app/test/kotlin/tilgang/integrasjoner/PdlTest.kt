package tilgang.integrasjoner

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tilgang.fakes.Fakes
import tilgang.fakes.WithFakes
import tilgang.integrasjoner.pdl.PdlGraphQLGateway

@WithFakes
class PdlTest {
    private val redis = Fakes.getRedisServer()
    private val prometheus = Fakes.getPrometheus()

    @Test
    fun `Kan parse hentPersonBolk`() = runTest {
        val test = PdlGraphQLGateway(redis, prometheus).hentPersonBolk(listOf("1234"), "test")

        assertThat(test?.size).isEqualTo(1)
    }
}
