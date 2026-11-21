# Audyt funkcjonalny: Settings + theme + AppReset

**Zakres:** `SettingsSheet.kt`, `SettingsStore.kt`, `ThemeMode.kt`, `Theme.kt`, `Color.kt`, `AppReset.kt`, `themes.xml` (+ `values-night`), oraz ścieżki wywołań (`MainActivity`, `ReminderActivity`, `AlarmScheduler`, widget).

**Data:** 2026-11-21 · branch bazowy: `cursor/fix-stock-on-insert-confirm-ec66`

---

## Werdykt

Motyw SYSTEM/LIGHT/DARK + AMOLED (#000) i DayNight XML są spójne. **Krytyczny regres:** zmiana godziny przypomnień woła `saveSetup` z kotwicą `current_insert` i **nadpisuje `start_date`**. Demo potwierdzenia działa (bez mark/wibracji), ale `singleTask` bez `onNewIntent` koliduje z prawdziwym alarmem. `AppReset` jest poprawny technicznie, lecz **odłączony od UI** (PR #9 usunął „Wyzeruj”). Gest swipe-back w Settings jest praktycznie martwy i konfliktuje z pagerem.

---

## P0 — krytyczne

### P0-1 · `applyReminderTime` → `saveSetup` nadpisuje `start_date`

| | |
|---|---|
| **Pliki** | `SettingsSheet.kt:41-50`, `SettingsStore.kt:26-34` |
| **Objaw** | Po potwierdzonym włożeniu (`current_insert` ≠ oryginalne `start_date`) zmiana chipa godziny zapisuje kotwicę cyklu jako nowe `start_date`. Oryginalna data z kreatora znika bezpowrotnie. |
| **Dlaczego** | `startDate` w sheet = `loadCurrentInsert() ?: loadStartDate()`. `saveSetup` zawsze robi `putString(KEY_START_DATE, …)` + `setup_done` + `weeks` + `reminder_time`. Brak API typu `saveReminderTime` only. |
| **Skutek** | Cicha korupcja prefs przy każdej zmianie godziny; rozjazd historii / ewentualnych ścieżek czytających wyłącznie `start_date`. Harmonogram alarmów akurat używa `current_insert` (OK), więc bug jest łatwy do przeoczenia w QA. |
| **Fix** | Dodać `SettingsStore.saveReminderTime(time)` (tylko `KEY_REMINDER`); w Settings wołać to + `AlarmScheduler.scheduleDaily` / `ReminderScheduler.rescheduleFromSettings`. Nie wołać `saveSetup` poza kreatorem. |

```41:50:app/src/main/java/emilka/myring/SettingsSheet.kt
    fun applyReminderTime(time: LocalTime) {
        reminderTime = time
        val anchor = startDate
        SettingsStore.saveSetup(context, anchor, reminderTime = time)
        // ...
        ReminderScheduler.schedule(context, anchor, SettingsStore.CYCLE_WEEKS, time)
```

```26:34:app/src/main/java/emilka/myring/SettingsStore.kt
    fun saveSetup(context: Context, startDate: LocalDate, weeks: Int = CYCLE_WEEKS, reminderTime: LocalTime) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SETUP_DONE, true)
            .putString(KEY_START_DATE, startDate.format(ISO))
            .putInt(KEY_WEEKS, CYCLE_WEEKS)
            .putString(KEY_REMINDER, reminderTime.toString())
            .apply()
    }
```

---

## P1 — wysokie

### P1-1 · Podwójne / redundantne przeplanowanie przy zmianie godziny

| | |
|---|---|
| **Pliki** | `SettingsSheet.kt:45-47`, `Notifications.kt` (`AlarmScheduler.scheduleDaily` → `rescheduleFromSettings`) |
| **Objaw** | `scheduleDaily` już woła `ReminderScheduler.rescheduleFromSettings`, potem sheet woła `ReminderScheduler.schedule(anchor, …)` drugi raz (cancel+set). |
| **Fix** | Jedna ścieżka: zapisz godzinę → `rescheduleFromSettings()` (albo tylko `schedule`). |

### P1-2 · Demo `ReminderActivity` + `launchMode=singleTask` bez `onNewIntent`

| | |
|---|---|
| **Pliki** | `AndroidManifest.xml:45-52`, `ReminderActivity.kt:35-72`, `SettingsSheet.kt:53-58` |
| **Objaw** | Drugie otwarcie (demo podczas realnego alarmu lub odwrotnie) trafia w istniejącą Activity; `onCreate` się nie powtarza, extras (`demo` / `insert`) nie są zastosowane. UI/wibracje/mark mogą zostać w złym trybie. |
| **Fix** | `onNewIntent` + `setIntent` + przebudowa contentu; albo osobny `demo` flag + `FLAG_ACTIVITY_CLEAR_TOP` / inny `launchMode` dla podglądu; albo Compose demo w sheet zamiast Activity. |

### P1-3 · Demo nadal woła `ReminderCenter.stopService` przy zamknięciu

| | |
|---|---|
| **Pliki** | `ReminderActivity.kt:55-62`, `74-80`, `ConfirmationScreen` path `stopService` |
| **Objaw** | Podgląd z Settings może zgasić działający FGS / powiadomienie prawdziwego alarmu (`stopService` + `cancelAlarmNotifications`). Copy mówi „bez zmiany cyklu”, nie wspomina o gaszeniu alarmu. |
| **Fix** | W `demo` nie wołać `stopService` / nie cancelować notyfikacji alarmu w `onDestroy`. |

### P1-4 · Gest swipe-back w Settings: zepsuty próg + konflikt z pagerem

| | |
|---|---|
| **Pliki** | `SettingsSheet.kt:65-69`, `MainActivity.kt:458-466` (`onBack` → `ringPage`), HorizontalPager |
| **Objaw** | `detectHorizontalDragGestures` dostaje **deltę per event**, zwykle ≪ 50px → warunek prawie nigdy nie wchodzi. Gdyby wszedł: `onBack` skacze na stronę okręgu (1), podczas gdy naturalny swipe pagera idzie na kalendarz (2). Przycisk „Wróć” usunięty (PR #9) — zostaje Back systemowy (OK) i pager. |
| **Fix** | Usunąć custom gesture (pager wystarczy) **albo** akumulować drag i spójnie scrollować o 1 stronę. |

### P1-5 · `AppReset` odłączony od UI; `clearSetup` sam w sobie nadal niebezpieczny

| | |
|---|---|
| **Pliki** | `AppReset.kt:6-14`, `SettingsStore.kt:136-141`, brak wywołań `AppReset` w `.kt` |
| **Objaw** | PR #9 usunął „Wyzeruj”. `AppReset.resetToSetup` jest martwy z perspektywy produktu. Bezpośrednie `clearSetup()` (gdyby ktoś dodał UI bez `AppReset`) znowu zostawi alarmy — historyczne P0 z `AUDYT.md` #3. |
| **Fix** | Albo przywrócić reset w Settings przez `AppReset` + nawigacja do Setup (`started=false`), albo usunąć/ukryć API i zaktualizować komentarz. |

### P1-6 · `AppReset` nie czyści `myring_prefs` ani aktywnego FGS

| | |
|---|---|
| **Pliki** | `AppReset.kt`, `Notifications.kt:158-184` (`myring_prefs` / `reminder_hour`/`reminder_min`) |
| **Objaw** | Po resecie zostaje stara godzina w `AlarmScheduler` prefs; `ReminderService` / alarm notifications nie są stopowane. `rescheduleFromSettings` early-return bez `start_date` limituje szkodę, ale stan jest brudny. |
| **Fix** | W `resetToSetup`: wyczyść `myring_prefs`, `ReminderCenter.stopService`, opcjonalnie zachować `theme_preference` przed `clear()`. |

---

## P2 — średnie / niskie

### P2-1 · Brak `saveReminderTime`; `weeks` w `saveSetup` ignorowane

| | |
|---|---|
| **Pliki** | `SettingsStore.kt:26-31`, `42` (`loadWeeks` → zawsze `CYCLE_WEEKS`) |
| **Objaw** | Parametr `weeks` martwy; `KEY_WEEKS` zapisywany zawsze jako 3. Komentarz przy `reloadCycleState` odwołuje się do nieistniejącego `SettingsStore.resetAppData`. |
| **Fix** | Usunąć martwy parametr / naprawić KDoc; dodać wąskie API zapisu godziny. |

### P2-2 · Podwójny magazyn godziny (`myring_settings` vs `myring_prefs`)

| | |
|---|---|
| **Pliki** | `SettingsStore` `KEY_REMINDER`, `AlarmScheduler` `KEY_HOUR`/`KEY_MIN` |
| **Objaw** | Dwa źródła prawdy; fallback w `rescheduleFromSettings` łagodzi, ale dryf możliwy przy częściowym zapisie. |
| **Fix** | Jedno źródło (preferowane: `myring_settings.reminder_time`). |

### P2-3 · Settings: tylko 4 chipy godzin; custom z kreatora nieedytowalny

| | |
|---|---|
| **Pliki** | `SettingsSheet.kt:127-134`, `SetupScreen.kt` `StepTimeRange` / `AutoTimePicker` |
| **Objaw** | Setup pozwala np. 19:37; Settings pokazuje tekst, żaden chip selected; powrót do custom tylko przez re-setup. Data włożenia tylko do odczytu (OK świadomie). |
| **Fix** | Ten sam time picker co w Setup, albo chip „Inna…”. |

### P2-4 · Theme SYSTEM/LIGHT/DARK — głównie OK; drobne niespójności AMOLED

| | |
|---|---|
| **Pliki** | `ThemeMode.kt:8-21`, `Theme.kt:70-111`, `Color.kt:24-37`, `themes.xml` + `values-night/themes.xml`, `MainActivity.kt:523` (`Color(0xFF121214)`), `MyringGlanceColorScheme.kt:70-76` |
| **OK** | `MyringApplication` + `MainActivity`/`ReminderActivity` wołają `ThemeMode.applyFromStore` przed UI; Compose i AppCompat DayNight zsynchronizowane; widget `colorsFor(preference)` respektuje LIGHT/DARK/SYSTEM; `DarkBackground = #000000`; `window_background` night = `#000000`. |
| **Objaw** | Kalendarz Surface używa hardcoded `#121214` zamiast `DarkSurface` (`#0E0E10`); nie pure AMOLED panel. Status/nav light flags w XML DayNight OK; Compose SideEffect nadpisuje przy starcie. |
| **Fix** | Ujednolicić kalendarz do tokenów z `Color.kt`. |

### P2-5 · Demo: hardcoded kolory kresek / kropek

| | |
|---|---|
| **Pliki** | `ReminderActivity.kt:219-227` (`Color(0xFFB0B0C0)`, `Color.White`, `Color(0xFFE47C9C)`) |
| **Objaw** | Słaby kontrast / brak adaptacji do motywu (AMOLED vs jasny). |
| **Fix** | Kolory z `MaterialTheme.colorScheme` / tokenów marki. |

### P2-6 · `clearSetup()` czyści też `theme_preference`

| | |
|---|---|
| **Pliki** | `SettingsStore.kt:136-140` |
| **Objaw** | Pełny reset wraca do SYSTEM — może zaskoczyć użytkownika ciemnego motywu. |
| **Fix** | Zachować `KEY_THEME` przy resecie cyklu. |

### P2-7 · Backup wyklucza prefs — OK

| | |
|---|---|
| **Pliki** | `backup_rules.xml`, `data_extraction_rules.xml` |
| **OK** | `myring_settings` / `myring_reminders` / `myring_prefs` wykluczone — nie przywracają zombie-alarmów po restore. |

---

## Mapa kluczy prefs

| Store | Klucze | Kto czyta/pisze |
|-------|--------|-----------------|
| `myring_settings` | `setup_done`, `start_date`, `weeks`, `reminder_time`, `theme_preference`, `current_insert`, `next_insert`, `last_removed`, `stock_prepaid_insert` | `SettingsStore`; theme: Settings sheet + `ThemeMode` |
| `myring_reminders` | `snooze_until`, `ring_actioned`, `ring_inserted` | `ReminderCenter`; czyszczone w `AppReset` / `clearReminderState` |
| `myring_prefs` | `reminder_hour`, `reminder_min` | `AlarmScheduler` only; **nie** czyszczone w `AppReset` |

---

## Ścieżki — co działa

| Scenariusz | Stan |
|------------|------|
| Theme SYSTEM → LIGHT → DARK | Zapis `theme_preference`, `onThemeChanged` → Compose + `AppCompatDelegate` + refresh widgetu |
| AMOLED dark | `#000` background Compose + night `window_background`; splash black |
| Reminder time chips → alarmy | Przeplanowanie działa (przy okazji P0 nadpisuje `start_date`) |
| Demo insert/remove | Bez `markInserted`/`markRemoved`, bez wibracji `LaunchedEffect`; snooze nie zapisuje `snooze_until` |
| Reset alarmów w `AppReset` | `cancelAllAlarms` + `clearReminderState` + `cancelMidnight` — poprawne, gdyby UI wołało |
| DayNight XML | `Theme.AppCompat.DayNight.NoActionBar` (fix #16) |

---

## Sugerowana kolejność napraw

1. **P0-1** — `saveReminderTime` / przestań wołać `saveSetup` z Settings  
2. **P1-2 / P1-3** — izolacja demo od prawdziwego alarmu  
3. **P1-4** — gest vs pager  
4. **P1-5 / P1-6** — decyzja produktowa: reset w UI albo usunięcie martwego API + domknięcie czyszczenia prefs  
5. **P2** — time picker, tokeny AMOLED, jeden magazyn godziny  

---

## Poza zakresem (świadomie)

Logika cyklu/zapasu/kalendarza spoza Settings (pokryta w `AUDYT.md` v1.2) — tu tylko gdy styka się z Settings/theme/reset.
