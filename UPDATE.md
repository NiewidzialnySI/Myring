# Autoaktualizacja — Moje Krążki (Myring)

Repo jest **publiczne** — apka czyta `update.json` bez tokena:

`https://raw.githubusercontent.com/NiewidzialnySI/Myring/main/update.json`

## Jak wypuścić nową wersję

1. W `app/build.gradle.kts` podnieś `versionCode` i `versionName`.
2. Zbuduj APK: `./gradlew :app:assembleRelease`.
3. Utwórz [GitHub Release](https://github.com/NiewidzialnySI/Myring/releases) z tagiem np. `v1.1` i dołącz plik `.apk` (np. `myring-v1.1.apk`).
4. Zaktualizuj `update.json` w `main`:

```json
{
  "versionCode": 2,
  "versionName": "1.1",
  "apkUrl": "https://github.com/NiewidzialnySI/Myring/releases/download/v1.1/myring-v1.1.apk",
  "changelog": "Co nowego…"
}
```

5. Commit + push `update.json`.

Przy następnym starcie apka pokaże dialog „Dostępna aktualizacja” (Zaktualizuj / Później).
Zaktualizuj → zgoda na nieznane źródła (jeśli trzeba) → pobranie APK → instalator systemowy.

## Uwaga

Instalacja idzie przez `Intent` + FileProvider. Cicha instalacja bez kliknięcia wymaga Device Owner / root / Shizuku.

Nie commituj `local.properties`, keystore (`klucz`, `*.jks`) ani plików `.apk` — są w `.gitignore`.
