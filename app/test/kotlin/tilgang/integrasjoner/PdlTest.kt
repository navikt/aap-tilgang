package tilgang.integrasjoner

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tilgang.fakes.Fakes
import tilgang.fakes.WithFakes
import tilgang.integrasjoner.pdl.PdlGraphQLGateway

@WithFakes
class PdlTest {

    @Test
    fun `Kan parse hentPersonBolk`() {
        val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

        val test = PdlGraphQLGateway(Fakes.redis, prometheus).hentPersonBolk(listOf("1234"), "test")

        assertThat(test?.size).isEqualTo(1)
    }
}
