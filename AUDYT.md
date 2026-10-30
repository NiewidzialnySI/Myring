# Audyt Myring v1.1

## Uprawnienia

| Uprawnienie | Potrzeba |
|---|---|
| `POST_NOTIFICATIONS` | Powiadomienia (dzień przed + alarm) |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Exact alarmy po Doze |
| `USE_FULL_SCREEN_INTENT` | Ekran potwierdzenia nad lockscreenem |
| `SYSTEM_ALERT_WINDOW` | Overlay (setup) |
| `FOREGROUND_SERVICE` + `SPECIAL_USE` | Krótki FGS przy alarme |
| `RECEIVE_BOOT_COMPLETED` | Replan po restarcie |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Hint OEM |
| `INTERNET` + `REQUEST_INSTALL_PACKAGES` | Autoaktualizacja |
| `CAMERA` | Skan EAN (opcjonalnie) |

Setup UI prowadzi do ustawień systemowych przy brakach (powiadomienia, exact, bateria, overlay).

## Alarmy i powiadomienia

- Osobne kanały: `myring_alarm_v2` (HIGH) i `myring_day_before_v2` (DEFAULT)
- Dzień wyjęcia/włożenia: `setAlarmClock` (najwyższa gwarancja)
- Dzień przed: `setExactAndAllowWhileIdle`
- FGS kończy się przez `STOP_FOREGROUND_REMOVE` — bez wiszącego sticky
- Usunięto codzienne puste powiadomienie „Czas na przypomnienie!”
- Naprawione kolizje `requestCode` alarmów (200/210/220/230 + cykl)
- Boot / `MY_PACKAGE_REPLACED` / zmiana daty/strefy → replan

## Widget

- `updatePeriodMillis=30 min` (backup; system i tak throttle’uje)
- Exact alarm 00:01 codziennie (`WidgetRefreshReceiver`)
- `DATE_CHANGED` / `TIMEZONE_CHANGED` / `TIME_SET`
- `refreshAll` po zmianie cyklu, zapasów, potwierdzeniu

## Autoaktualizacja

- `update.json` + `UPDATE.md`
- `BuildConfig.UPDATE_MANIFEST_URL`
- Dialog przy starcie: Zaktualizuj / Później
- FileProvider (`cache/apk/`)

## Bezpieczeństwo git

W `.gitignore`: `local.properties`, `klucz`, `*.jks` / `*.keystore`, `*.apk`, `/app/release/`.
Nie commitować APK ani keystore.
