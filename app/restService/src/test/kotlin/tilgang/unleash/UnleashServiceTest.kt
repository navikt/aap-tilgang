package tilgang.unleash

import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UnleashServiceTest {
    @Test
    fun `unknown toggle is disabled and changes are evaluated on each call`() {
        val unleash = FakeUnleash()
        val service = UnleashService(unleash)
        val feature = FeatureToggle.TilgangsmaskinRaskRobustHttpklient

        assertThat(feature.key()).isEqualTo("TilgangsmaskinRaskRobustHttpklient")
        assertThat(service.isEnabled(feature)).isFalse()

        unleash.enable(feature.key())
        assertThat(service.isEnabled(feature)).isTrue()

        unleash.disable(feature.key())
        assertThat(service.isEnabled(feature)).isFalse()
    }

    @Test
    fun `passes user ID to Unleash context`() {
        val unleash = FakeUnleash()
        val feature = FeatureToggle.TilgangsmaskinRaskRobustHttpklient
        unleash.conditionallyEnable({ it.userId.orElse("") == "test-user" }, feature.key())
        val service = UnleashService(unleash)

        assertThat(service.isEnabled(feature, "test-user")).isTrue()
        assertThat(service.isEnabled(feature, "another-user")).isFalse()
    }

    @Test
    fun `shuts down SDK when closed`() {
        val unleash = mockk<Unleash>(relaxed = true)

        UnleashService(unleash).close()

        verify(exactly = 1) { unleash.shutdown() }
    }
}
