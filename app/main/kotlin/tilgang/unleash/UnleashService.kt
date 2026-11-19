package tilgang.unleash

import io.getunleash.DefaultUnleash
import io.getunleash.Unleash
import io.getunleash.UnleashContext
import io.getunleash.util.UnleashConfig
import no.nav.aap.komponenter.config.requiredConfigForKey

class UnleashService(
    private val unleash: Unleash = DefaultUnleash(
        UnleashConfig.builder()
            .appName(requiredConfigForKey("NAIS_APP_NAME"))
            .unleashAPI("${requiredConfigForKey("UNLEASH_SERVER_API_URL")}/api")
            .apiKey(requiredConfigForKey("UNLEASH_SERVER_API_TOKEN"))
            .build()
    ),
) : AutoCloseable {
    fun isEnabled(featureToggle: FeatureToggle): Boolean = unleash.isEnabled(featureToggle.key())

    fun isEnabled(featureToggle: FeatureToggle, userId: String): Boolean {
        val context = UnleashContext.builder().userId(userId).build()
        return unleash.isEnabled(featureToggle.key(), context)
    }

    override fun close() {
        unleash.shutdown()
    }
}
