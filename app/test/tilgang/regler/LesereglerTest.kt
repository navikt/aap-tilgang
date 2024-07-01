package tilgang.regler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tilgang.Rolle
import tilgang.integrasjoner.pdl.Gradering
import tilgang.integrasjoner.pdl.PersonResultat

class LesereglerTest {
    @Test
    fun `Saksbehandler uten fortrolige-roller skal ikke kunne lese fortrolige adresser`() {
        val ident = "1234"
        val roller = listOf(Rolle.SAKSBEHANDLER)
        val personListe1 = listOf(PersonResultat("1000", listOf(Gradering.FORTROLIG), "kode"))
        val personerListe2 = listOf(PersonResultat("1234", listOf(Gradering.STRENGT_FORTROLIG), "kode"))
        val personerListe3 = listOf(PersonResultat("5678", listOf(Gradering.STRENGT_FORTROLIG_UTLAND), "kode"))

        assertFalse(harLesetilgang(ident, roller, personListe1))
        assertFalse(harLesetilgang(ident, roller, personerListe2))
        assertFalse(harLesetilgang(ident, roller, personerListe3))
    }

    @Test
    fun `Rolle STRENGT_FORTROLIG_ADRESSE har tilgang til person med fortrolig og strengt fortrolig adresse`() {
        val ident = "1222"
        val roller = listOf(Rolle.STRENGT_FORTROLIG_ADRESSE)
        val personer = listOf(
            PersonResultat("1000", listOf(Gradering.FORTROLIG), "kode"),
            PersonResultat("1234", listOf(Gradering.STRENGT_FORTROLIG), "kode"),
            PersonResultat("5678", listOf(Gradering.STRENGT_FORTROLIG_UTLAND), "kode")
        )
        assertTrue(harLesetilgang(ident, roller, personer))
    }

    @Test
    fun `Rolle FORTROLIG_ADRESSE har tilgang til person med fortrolig, men ikke strengt fortrolig, adresse`() {
        val ident = "1234"
        val roller = listOf(Rolle.FORTROLIG_ADRESSE)
        val personListe1 = listOf(PersonResultat("1000", listOf(Gradering.FORTROLIG), "kode"))
        val personListe2 = listOf(
            PersonResultat("1000", listOf(Gradering.FORTROLIG), "kode"),
            PersonResultat("1234", listOf(Gradering.STRENGT_FORTROLIG), "kode"),
            PersonResultat("5678", listOf(Gradering.STRENGT_FORTROLIG_UTLAND), "kode")
        )
        assertTrue(harLesetilgang(ident, roller, personListe1))
        assertFalse(harLesetilgang(ident, roller, personListe2))
    }

    @Test
    fun `Saksbehandler skal ikke ha tilgang til egen sak`() {
        val ident = "1234"
        val roller = listOf(Rolle.SAKSBEHANDLER)
        val personListe = listOf(PersonResultat("1234", emptyList(), "kode"))
        assertFalse(harLesetilgang(ident, roller, personListe))
    }

}