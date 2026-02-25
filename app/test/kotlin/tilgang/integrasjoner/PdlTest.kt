package tilgang.integrasjoner

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tilgang.TestRedis
import tilgang.integrasjoner.pdl.PdlGraphQLGateway
import tilgang.redis.Redis
import tilgang.redis.WithRedis

@WithRedis
class PdlTest {
    private val redis: Redis = TestRedis.server

    @Test
    fun `Kan parse hentPersonBolk`() {
        val prometheus = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

        val test = PdlGraphQLGateway(redis, prometheus).hentPersonBolk(listOf("1234"), "test")

        assertThat(test?.size).isEqualTo(1)
    }
}
