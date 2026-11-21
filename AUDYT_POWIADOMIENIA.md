# Audyt powiadomień / alarmów (2026-11-21)

Wynik równoległych audytów (powiadomienia, cykl/zapas, UI, widget).

## Werdykt

Przypomnienia **nie zawsze** odpalały się niezawodnie. Główne dziury: brak catch-up tego samego dnia po godzinie, FGS bez Activity, Setup na API 31–32, brak reschedule o północy.

## Wdrożone poprawki (ten PR)

| Problem | Fix |
|---------|-----|
| Alarm „dziś po godzinie” gubiony | `resolveAlarmDateTime` → catch-up +2 min |
| FGS fail bez ekranu | `ReminderService` zawsze otwiera `ReminderActivity` |
| Setup blokuje na API 12 | `notifGranted` = `areNotificationsEnabled` |
| Uprawnienia wracają bez reschedule | `MainActivity.onResume` → `ReminderScheduler` |
| Północ tylko widget | `WidgetRefreshReceiver` też reschedule alarmów |
| `refreshAll` gubiony po boot | `goAsync` + `refreshAllAwait` |
| Double debit zapasu (prepaid) | `markInserted` respektuje prepaid |
| Insert bez remove na Home | CTA tylko gdy `nextBreakEnd != null` dla insert; overdue OK |
| Wstecz zamyka apkę | `BackHandler` → okrąg / `moveTaskToBack` |
| Backup API 31+ | `data_extraction_rules` wyklucza prefs |
| Biały flash | `MyringApplication` + ThemeMode |
| Widget motyw aplikacji | Glance `colorsFor(preference)` |

## Pozostałe (P1/P2, nie w tym PR)

- `canUseFullScreenIntent` UX na API 34+
- Re-nag co 15/60 min do potwierdzenia
- Fazy Home: RING_ON do potwierdzenia wyjęcia
- `CycleMarking.dayMark` INSERT vs remove
- OEM `QUICKBOOT_POWERON`
