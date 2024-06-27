package tilgang.regler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tilgang.Rolle
import tilgang.integrasjoner.pdl.Gradering
import tilgang.integrasjoner.pdl.PersonResultat

class LesereglerTest {

    @Test
    fun `Må ha rolle for å lese fortrolige adresser`() {
        val roller = emptyList<Rolle>()
        val personListe1 = listOf(PersonResultat("1000", listOf(Gradering.FORTROLIG), "kode"))
        val personerListe2 = listOf(PersonResultat("1234", listOf(Gradering.STRENGT_FORTROLIG), "kode"))
        val personerListe3 = listOf(PersonResultat("5678", listOf(Gradering.STRENGT_FORTROLIG_UTLAND), "kode"))

        assertFalse(harTilgangTilPersoner(roller, personListe1))
        assertFalse(harTilgangTilPersoner(roller, personerListe2))
        assertFalse(harTilgangTilPersoner(roller, personerListe3))
    }

    @Test
    fun `Rolle STRENGT_FORTROLIG_ADRESSE har tilgang til person med fortrolig og strengt fortrolig adresse`() {
        val roller = listOf(Rolle.STRENGT_FORTROLIG_ADRESSE)
        val personer = listOf(
            PersonResultat("1000", listOf(Gradering.FORTROLIG), "kode"),
            PersonResultat("1234", listOf(Gradering.STRENGT_FORTROLIG), "kode"),
            PersonResultat("5678", listOf(Gradering.STRENGT_FORTROLIG_UTLAND), "kode")
        )
        assertTrue(harTilgangTilPersoner(roller, personer))
    }

    @Test
    fun `Rolle FORTROLIG_ADRESSE har tilgang til person med fortrolig, men ikke strengt fortrolig, adresse`() {
        val roller = listOf(Rolle.FORTROLIG_ADRESSE)
        val personListe1 = listOf(PersonResultat("1000", listOf(Gradering.FORTROLIG), "kode"))
        val personListe2 = listOf(
            PersonResultat("1000", listOf(Gradering.FORTROLIG), "kode"),
            PersonResultat("1234", listOf(Gradering.STRENGT_FORTROLIG), "kode"),
            PersonResultat("5678", listOf(Gradering.STRENGT_FORTROLIG_UTLAND), "kode")
        )
        assertTrue(harTilgangTilPersoner(roller, personListe1))
        assertFalse(harTilgangTilPersoner(roller, personListe2))
    }

}