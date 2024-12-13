# Tilgang-plugin
Hensikten til denne modulen er å tilby et grensesnitt for å kalle `tilgang`-tjenesten på en konsekvent måte. Modulen
inneholder følgende:

- **AuthourizedClient**: Eksponerer extension-metoder for autoriserte get-, post-, og put-endepunkt. Disse kan anvendes
i de andre modulene for å kalle `tilgang` automatisk etter autentisering. Dette gjøres ved at `TilgangPlugin` blir
installert i routen.

- **TilgangPlugin**: Route scoped plugin for å intercepte http-kall og kalle `tilgang`-tjenesten. Attributter blir utledet fra
  path parameters eller request body, samt operasjonen som er definert ved bruk av metodene i `AuthorizedClient`.
`tilgang`-tjenesten kalles for autorisering av tilgang requests med on-behalf-of tokens. For requests med
client-credentials token, vil valideringen skje uten å kalle `tilgang`-tjenesten. Se eksempler under.

- **TilgangGateway**: Klienten som kaller `tilgang`-tjenesten. Denne brukes av `TilgangPlugin`, men kan også tas i bruk
direkte i de tilfellene det er mer hensiktsmessig.

- **TilgangInspections**: Det er skrevet custom inspections for å gi advarsel dersom `get` og `post` brukes direkte. I
de fleste tilfeller ønsker vi å bruke de tilsvarende <i>autoriserte</i> funksjonene, som vi finner i `AuthorizedClient`.
Det anbefales å importere denne inspeksjonsfilen i IDE-en.

## TilgangInspections
Det er skrevet custom inspections for å gi advarsel dersom `get` og `post` brukes direkte. I de fleste tilfeller ønsker vi å bruke de tilsvarende <i>autoriserte</i> funksjonene, som vi finner i `AuthorizedClient`. Det anbefales å importere denne inspeksjonsfilen i IDE-en.

## Bumpe Gradle wrapper

```bash
./gradlew wrapper --gradle-version=8.11
```
## Ta i bruk TilgangPlugin

Finn siste versjon i [releases](https://github.com/navikt/aap-tilgang/releases) legg til avhengighet.

```kotlin
dependencies {
    implementation("no.nav.aap.tilgang:plugin:$tilgangVersjon")
}
```

## Eksempler
Se [AutorisertEksempelApp.kt](src/test/kotlin/AutorisertEksempelApp.kt) for eksempler. Der er eksempler på routes som
autoriserer tilgang for requests med on-behalf-of tokens, client-credentials tokens, eller begge deler.
