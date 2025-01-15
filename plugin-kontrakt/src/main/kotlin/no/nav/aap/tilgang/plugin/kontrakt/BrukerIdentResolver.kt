package no.nav.aap.tilgang.plugin.kontrakt

/**
 * Brukes i forbindelse med auditlogging for å finne fnr for bruker det gjøres oppslag på.
 * For eksempel ved å slå opp ident i databasen vha. saksnummer
 */
fun interface BrukerIdentResolver {
    fun resolve(referanse: String): String
}