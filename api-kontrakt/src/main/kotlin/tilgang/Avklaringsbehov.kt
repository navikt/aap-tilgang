package tilgang

enum class Avklaringsbehov(val kode: String) {
    MANUELT_SATT_PÅ_VENT("9001"),
    AVKLAR_STUDENT("5001"),
    AVKLAR_SYKDOM("5003"),
    FASTSETT_ARBEIDSEVNE("5004"),
    FRITAK_MELDEPLIKT("5005"),
    AVKLAR_BISTANDSBEHOV("5006"),
    VURDER_SYKEPENGEERSTATNING("5007"),
    FASTSETT_BEREGNINGSTIDSPUNKT("5008"),
    FORESLÅ_VEDTAK("5098"),
    FATTE_VEDTAK("5099");

    companion object {
        private val map = entries.associateBy(Avklaringsbehov::kode)
        fun fraKode(kode: String) = map[kode] ?: throw IllegalArgumentException("Finner ikke Avklaringsbehovtype for kode: $kode")
    }
}