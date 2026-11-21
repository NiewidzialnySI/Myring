# Moje Krążki (Myring)

Aplikacja Android do śledzenia cyklu krążka dopochwowego: odliczanie dni, przypomnienia (dzień przed + dzień wyjęcia), zapas opakowań i widget.

Package: `emilka.myring`

## Uruchomienie

```bash
./gradlew :app:assembleDebug
# lub
./gradlew :app:assembleRelease
```

APK release (stały podpis, ten sam przy każdym buildzie):

```bash
chmod +x scripts/build-release-apk.sh
./scripts/build-release-apk.sh
```

Wynik: `app/build/outputs/apk/release/app-release.apk`. Keystore i hasła: [`signing/README.md`](signing/README.md) (w repozytorium).

Wymaga Android SDK (compileSdk 37). Ustaw `sdk.dir` w `local.properties`.

## Co robi ta wersja

- Exact alarmy + full-screen przypomnienie w dniu wyjęcia/włożenia
- Lekkie powiadomienie dzień wcześniej
- Widget z liczbą dni — odświeżany o północy i po zmianach cyklu/zapasów
- Autoaktualizacja z `update.json` (patrz [UPDATE.md](UPDATE.md))

## Zapas po reinstalacji

Na ekranie głównym **przytrzymaj** górny pasek kółek (zapas) → wpisz ile krążków zostało.

## Bezpieczeństwo

Do gita nie trafiają: `local.properties`, `local.properties`, pliki `.apk`. **Release keystore** jest w `signing/` celowo — żeby nie gubić podpisu; hasło w `signing/README.md`.
