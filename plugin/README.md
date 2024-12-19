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
Det er skrevet [custom inspections](https://www.jetbrains.com/help/idea/creating-custom-inspections.html) for å gi advarsel dersom `get` og `post` brukes direkte. I de fleste tilfeller ønsker vi å bruke de tilsvarende <i>autoriserte</i> funksjonene, som vi finner i `AuthorizedClient`. Det anbefales å importere inspeksjonsfilen `TilgangInspections.xml` i IDE-en:
`Settings > Editor > Inspections > Import Profile > Velg TilgangInspections.xml`
P.d. støtter ikke inspeksjonen generics i TParams, TRequest og TResponse, så vær obs på typene her.

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

Ved autorisering av requests med on-behalf-of tokens må man spesifisere kontekst man ber om tilgang for, som per nå er
enten en sak, en behandling eller en journalpost. Denne konteksten definerer man for GET-requests ved å spesifisere
`SakPathParam`, `BehandlingPathParam` eller `JournalpostPathParam` i `AuthorizationParamPathConfig`. For POST-requests
må request body være en sub-type av `TilgangReferanse`.

Det er ikke alltid man har nødvendig kontekst for autorisering når man definerer en route. Dette kan være fordi konteksten
baserer seg på en ID/referanse som ikke er verken en sak, en behandling eller en journalpost, men der man har en kobling
mellom ID/referanse og f.eks. sak i databasen. Man har da følgende muligheter for autorisering:
- Foretrukket løsning er å lage nye resolvers, se etter hvordan det er løst for journalpost. Generaliser gjerne
implementasjonen av resolvers når det lages flere (per nå er det kun en resolver).
- Ikke bruk plugin, men bruk TilgangGateway.
- Endre på kontekst slik at man kan bruke plugin, altså endre route til å inkludere enten sak, behandling eller
journalpost. Husk å i tillegg sjekke at ID/referanse og sak, behandling eller journalpost hører sammen.
- Man _kan_ vurdere å lage en ny PIP (policy information point) som tilgang-tjenesten kan slå opp i, men det bør være
sentrale identifikatorer i domenet.

Ved autorisering av requests med client-credentials, altså en maskin-til-maskin integrasjon, autoriserer man et
forhåndsdefinert sett av applikasjoner. Dermed er det ikke behov for en ytterligere kontekst for hva man autoriserer. 
