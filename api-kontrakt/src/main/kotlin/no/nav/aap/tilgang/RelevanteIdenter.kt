package no.nav.aap.tilgang

data class RelevanteIdenter(val søker: List<String>, val barn: List<String>) {
    init {
        require(søker.isNotEmpty()) { "Søker må ha minst en ident" }
    }
}
