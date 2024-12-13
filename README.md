# aap-tilgang
Applikasjon for å sjekke om brukere har tilgang til Kelvin-ressurs

## Plugin
For integrasjon mot tilgang-tjenesten finnes det en plugin og en http-klient, se [plugin/README.md](plugin/README.md) 

## Komme i gang
Bruker gradle wrapper, så bare klon og kjør `./gradlew build`

### Github package registry
Miljøvariabelen `GITHUB_TOKEN` må være satt for å hente dependencies fra Github Package Registry.

Den skal være satt til din github personal access token.
Denne opprettes på Github ved å gå til settings -> developer settings. 
Husk `read:packages`-rettighet og enable SSO.


## Henvendelser
Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub

# For NAV-ansatte
Interne henvendelser kan sendes via Slack i kanalen #po-aap-team-aap.
