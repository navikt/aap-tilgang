package tilgang.kontroll.regler

data class AdressebeskyttelseInput(
    val roller: List<Adressebeskyttelsesrolle>,
    val graderinger: List<Adressegradering>,
)
