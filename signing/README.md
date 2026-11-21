# Podpis release APK (Moje Krążki)

W tym folderze leży **stały** keystore używany do każdego `assembleRelease`. Nie musisz go tworzyć od nowa.

| Plik | Rola |
|------|------|
| `myring-release.jks` | Keystore PKCS12 |
| `signing.properties` | Hasła i alias (czytane przez Gradle) |

- **Alias:** `myring`
- **Hasło keystore i klucza:** `MyringRelease2026!`

## Build release

```bash
./scripts/build-release-apk.sh
```

APK: `app/build/outputs/apk/release/app-release.apk`

## Ważne

- **Ta sama para kluczy** = możliwa aktualizacja apki nadpisaniem (bez odinstalowywania).
- Sklonuj repo na nowym komputerze — keystore jest w `signing/`, build od razu działa.
- Publiczne repozytorium z hasłem w plain text to ryzyko — u Ciebie to świadoma wygoda; w razie wycieku wygeneruj nowy klucz i podbij `versionCode`.

## Odtworzenie klucza (tylko gdy zgubiony)

```bash
keytool -genkeypair -v -storetype PKCS12 \
  -keystore signing/myring-release.jks -alias myring \
  -keyalg RSA -keysize 2048 -validity 36500 \
  -storepass 'MyringRelease2026!' -keypass 'MyringRelease2026!' \
  -dname "CN=Moje Krazki, OU=Android, O=Myring, C=PL"
```

Nowy klucz **nie** nadpisze starej instalacji na telefonie — trzeba odinstalować lub zachować ten sam plik `.jks`.