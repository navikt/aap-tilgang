package tilgang.kontroll.regler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AdressebeskyttelseRegelTest {
    @Test
    fun `Saksbehandler uten fortrolige-roller skal ikke kunne lese fortrolige adresser`() {
        val graderinger1 = listOf(Adressegradering.UGRADERT, Adressegradering.FORTROLIG)
        val graderinger2 = listOf(Adressegradering.STRENGT_FORTROLIG)
        val graderinger3 = listOf(Adressegradering.STRENGT_FORTROLIG_UTLAND)

        assertFalse(
            AdressebeskyttelseRegel.vurder(
                AdressebeskyttelseInput(
                    emptyList(),
                    graderinger1
                )
            )
        )
        assertFalse(
            AdressebeskyttelseRegel.vurder(
                AdressebeskyttelseInput(
                    emptyList(),
                    graderinger2
                )
            )
        )
        assertFalse(
            AdressebeskyttelseRegel.vurder(
                AdressebeskyttelseInput(
                    emptyList(),
                    graderinger3
                )
            )
        )
    }

    @Test
    fun `Rolle STRENGT_FORTROLIG_ADRESSE har tilgang til person med fortrolig og strengt fortrolig adresse`() {
        val graderinger = listOf(
            Adressegradering.FORTROLIG,
            Adressegradering.STRENGT_FORTROLIG,
            Adressegradering.STRENGT_FORTROLIG_UTLAND
        )
        assertTrue(
            AdressebeskyttelseRegel.vurder(
                AdressebeskyttelseInput(
                    listOf(
                        Adressebeskyttelsesrolle.STRENGT_FORTROLIG_ADRESSE
                    ), graderinger
                )
            )
        )
    }

    @Test
    fun `Rolle STRENGT_FORTROLIG_ADRESSE har tilgang til person med strengt fortrolig adresse utland`() {
        val graderinger = listOf(Adressegradering.STRENGT_FORTROLIG_UTLAND)
        assertTrue(
            AdressebeskyttelseRegel.vurder(
                AdressebeskyttelseInput(
                    listOf(
                        Adressebeskyttelsesrolle.STRENGT_FORTROLIG_ADRESSE
                    ), graderinger
                )
            )
        )
    }

    @Test
    fun `Rolle FORTROLIG_ADRESSE har tilgang til person med fortrolig, men ikke strengt fortrolig, adresse`() {
        val graderinger1 = listOf(Adressegradering.FORTROLIG)
        val graderinger2 = listOf(
            Adressegradering.FORTROLIG,
            Adressegradering.STRENGT_FORTROLIG,
            Adressegradering.STRENGT_FORTROLIG_UTLAND
        )
        assertTrue(
            AdressebeskyttelseRegel.vurder(
                AdressebeskyttelseInput(
                    listOf(
                        Adressebeskyttelsesrolle.FORTROLIG_ADRESSE
                    ), graderinger1
                )
            )
        )
        assertFalse(
            AdressebeskyttelseRegel.vurder(
                AdressebeskyttelseInput(
                    listOf(
                        Adressebeskyttelsesrolle.FORTROLIG_ADRESSE
                    ), graderinger2
                )
            )
        )
    }
}