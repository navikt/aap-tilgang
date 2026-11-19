package tilgang.unleash

enum class FeatureToggle {
    raskRobustTilgangsmaskin;

    fun key(): String = name
}
