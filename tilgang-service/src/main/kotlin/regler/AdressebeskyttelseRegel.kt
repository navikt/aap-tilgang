package tilgang.kontroll.regler

data object AdressebeskyttelseRegel : Regel<AdressebeskyttelseInput> {
    override fun vurder(input: AdressebeskyttelseInput): Boolean {
        return sjekkAdressebeskyttelse(input.roller, input.graderinger)
    }

    private fun sjekkAdressebeskyttelse(
        roller: List<Adressebeskyttelsesrolle>,
        adresseBeskyttelse: List<Adressegradering>
    ): Boolean {
        return adresseBeskyttelse.isEmpty()
                || adresseBeskyttelse.all { it === Adressegradering.UGRADERT }
                || (Adressebeskyttelsesrolle.STRENGT_FORTROLIG_ADRESSE in roller && finnStrengeste(adresseBeskyttelse) in listOf(Adressegradering.STRENGT_FORTROLIG, Adressegradering.STRENGT_FORTROLIG_UTLAND))
                || Adressebeskyttelsesrolle.FORTROLIG_ADRESSE in roller && finnStrengeste(adresseBeskyttelse) === Adressegradering.FORTROLIG
    }

    private fun finnStrengeste(adresseBeskyttelser: List<Adressegradering>): Adressegradering {
        return when {
            Adressegradering.STRENGT_FORTROLIG in adresseBeskyttelser -> Adressegradering.STRENGT_FORTROLIG
            Adressegradering.STRENGT_FORTROLIG_UTLAND in adresseBeskyttelser -> Adressegradering.STRENGT_FORTROLIG_UTLAND
            Adressegradering.FORTROLIG in adresseBeskyttelser -> Adressegradering.FORTROLIG
            else -> Adressegradering.UGRADERT
        }
    }
}
