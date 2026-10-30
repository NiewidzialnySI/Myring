# Moje Krążki (Myring)

Aplikacja Android do śledzenia cyklu krążka dopochwowego: odliczanie dni, przypomnienia (dzień przed + dzień wyjęcia), zapas opakowań i widget.

Package: `emilka.myring`

## Uruchomienie

```bash
./gradlew :app:assembleDebug
# lub
./gradlew :app:assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk` (podpisany kluczem debug — instalowalny).

Wymaga Android SDK (compileSdk 37). Ustaw `sdk.dir` w `local.properties`.

## Co robi ta wersja

- Exact alarmy + full-screen przypomnienie w dniu wyjęcia/włożenia
- Lekkie powiadomienie dzień wcześniej
- Widget z liczbą dni — odświeżany o północy i po zmianach cyklu/zapasów
- Autoaktualizacja z `update.json` (patrz [UPDATE.md](UPDATE.md))

## Bezpieczeństwo

Do gita nie trafiają: `local.properties`, keystore (`klucz`, `*.jks`), pliki `.apk`.
