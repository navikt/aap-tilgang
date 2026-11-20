package tilgang.unleash

enum class FeatureToggle {
    // Se: https://aap-unleash-web.iap.nav.cloud.nais.io/projects/default
    TilgangsmaskinRaskRobustHttpklient;

    fun key(): String = name
}
