package tilgang.regler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tilgang.service.AdressebeskyttelseGruppe
import tilgang.integrasjoner.pdl.Gradering
import tilgang.integrasjoner.pdl.PersonResultat
import java.util.*

class AdressebeskyttelseRegelTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            System.setProperty("fortrolig.adresse.ad", UUID.randomUUID().toString())
            System.setProperty("strengt.fortrolig.adresse.ad", UUID.randomUUID().toString())
        }
    }
    
    @Test
    fun `Saksbehandler uten fortrolige-roller skal ikke kunne lese fortrolige adresser`() {
        val personListe1 = listOf(
            PersonResultat("1111", listOf(Gradering.UGRADERT), "kode"),
            PersonResultat("1000", listOf(Gradering.FORTROLIG), "kode")
        )
        val personListe2 = listOf(PersonResultat("1234", listOf(Gradering.STRENGT_FORTROLIG), "kode"))
        val personListe3 = listOf(PersonResultat("5678", listOf(Gradering.STRENGT_FORTROLIG_UTLAND), "kode"))

        assertFalse(
            AdressebeskyttelseRegel.vurder(
                AdressebeskyttelseInput(
                    emptyList(),
                    personListe1
                )
            )
        )
        assertFalse(
            AdressebeskyttelseRegel.vurder(
                AdressebeskyttelseInput(
                    emptyList(),
                    personListe2
                )
            )
        )
        assertFalse(
            AdressebeskyttelseRegel.vurder(
                AdressebeskyttelseInput(
                    emptyList(),
                    personListe3
                )
            )
        )
    }

    @Test
    fun `Rolle STRENGT_FORTROLIG_ADRESSE har tilgang til person med fortrolig og strengt fortrolig adresse`() {
        val personer = listOf(
            PersonResultat("1000", listOf(Gradering.FORTROLIG), "kode"),
            PersonResultat("1234", listOf(Gradering.STRENGT_FORTROLIG), "kode"),
            PersonResultat("5678", listOf(Gradering.STRENGT_FORTROLIG_UTLAND), "kode")
        )
        assertTrue(
            AdressebeskyttelseRegel.vurder(
                AdressebeskyttelseInput(
                    listOf(
                        AdressebeskyttelseGruppe.STRENGT_FORTROLIG_ADRESSE
                    ), personer
                )
            )
        )
    }

    @Test
    fun `Rolle STRENGT_FORTROLIG_ADRESSE har tilgang til person med strengt fortrolig adresse utland`() {
        val personer = listOf(
            PersonResultat("5678", listOf(Gradering.STRENGT_FORTROLIG_UTLAND), "kode")
        )
        assertTrue(
            AdressebeskyttelseRegel.vurder(
                AdressebeskyttelseInput(
                    listOf(
                        AdressebeskyttelseGruppe.STRENGT_FORTROLIG_ADRESSE
                    ), personer
                )
            )
        )
    }

    @Test
    fun `Rolle FORTROLIG_ADRESSE har tilgang til person med fortrolig, men ikke strengt fortrolig, adresse`() {
        val personListe1 = listOf(PersonResultat("1000", listOf(Gradering.FORTROLIG), "kode"))
        val personListe2 = listOf(
            PersonResultat("1000", listOf(Gradering.FORTROLIG), "kode"),
            PersonResultat("1234", listOf(Gradering.STRENGT_FORTROLIG), "kode"),
            PersonResultat("5678", listOf(Gradering.STRENGT_FORTROLIG_UTLAND), "kode")
        )
        assertTrue(
            AdressebeskyttelseRegel.vurder(
                AdressebeskyttelseInput(
                    listOf(
                        AdressebeskyttelseGruppe.FORTROLIG_ADRESSE
                    ), personListe1
                )
            )
        )
        assertFalse(
            AdressebeskyttelseRegel.vurder(
                AdressebeskyttelseInput(
                    listOf(
                        AdressebeskyttelseGruppe.FORTROLIG_ADRESSE
                    ), personListe2
                )
            )
        )
    }
}