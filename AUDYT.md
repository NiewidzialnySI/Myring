# Audyt Myring — list 2025-11-21 (v1.2)

Uzupełnienie audytu v1.1 o **przegląd kodu** i rzeczy do poprawy. Stan repo: wersja 1.2, poprawka zapasu w PR #1 (zużycie krążka przy potwierdzeniu włożenia).

---

## Podsumowanie priorytetów

| Priorytet | Ile | Sens |
|-----------|-----|------|
| Krytyczne | 3 | Utrata danych / rozjazd UI z rzeczywistością |
| Wysokie | 8 | Logika cyklu, zapasów, alarmów |
| Średnie | 10 | UX, wydajność, utrzymanie |
| Niskie | 6 | Dopracowanie, testy, język |

**Sugerowana kolejność napraw:** (1) odświeżanie stanu cyklu w Compose po potwierdzeniu, (2) naprawa `useRing` / polityka jednego odejmowania zapasu, (3) alarmy przy `clearSetup` i po minionym dniu akcji, (4) kalendarz vs potwierdzone daty, (5) ustawienia użytkownika po kreatorze.

---

## Krytyczne

### 1. Ekran główny nie odświeża cyklu po potwierdzeniu wyjęcia/włożenia

**Pliki:** `MainActivity.kt` (`MyringApp`, `HomeScreen`)

Stan `effectiveAnchor` i `nextBreakDate` jest ładowany **raz** przy starcie (`remember { SettingsStore.load… }`). Po `ReminderActivity` prefs się zmieniają, widget dostaje `refreshAll`, ale **okrąg, faza i przycisk dnia akcji** liczą się na starych datach aż do pełnego restartu apki.

**Naprawa:** przy `onResume` / `LifecycleResumeEffect` w `MyringApp` ponownie odczytać `loadCurrentInsert()`, `loadNextInsert()`, zaktualizować stan przekazywany do `HomeScreen`.

### 2. `StockStore.useRing()` może skasować zużyte opakowania z bazy

**Plik:** `StockStore.kt`

`useRing` czyta przez `load()` (tylko pozycje z `remaining > 0`), potem `save(list)` — wpisy w pełni zużyte **znikają z JSON**.

Dziś woła tego nikt (używane są `useRingByBase` / `useNextRing` z `loadAll`), ale to pułapka przy każdej przyszłej zmianie.

**Naprawa:** jak w `useNextRing` — zawsze `loadAll()` → mutacja → `save()`. Opcjonalnie usunąć API lub oznaczyć `@Deprecated`.

### 3. Reset kreatora bez anulowania alarmów

**Pliki:** `SettingsStore.clearSetup()`, `MainActivity` (`onClearSetup`)

`clearSetup` czyści tylko `myring_settings`. Zaplanowane alarmy wyjęcia/włożenia zostają — mogą wybudzić użytkownika po „wyzerowaniu” apki.

**Naprawa:** przy resecie wywołać `ReminderScheduler.cancelAll` (lub równoważne), wyczyścić flagi w `myring_reminders` (`ring_actioned`, `ring_inserted`, `snooze_until`).

---

## Wysokie

### 4. Podwójne zużycie krążka (dodanie opakowania + potwierdzenie włożenia)

**Pliki:** `StockSheet.kt` („Użyj 1 krążek na obecny cykl”), `ReminderCenter.markInserted()` → `useNextRing()`

Przy pierwszym cyklu: checkbox przy dodaniu **już** odejmuje 1, potem potwierdzenie włożenia odejmuje drugi.

**Naprawa:** flaga „bieżący cykl już policzony w zapasie” albo odejmowanie **tylko** przy włożeniu (bez checkboxa) albo **tylko** przy dodaniu na start — jedna reguła biznesowa.

### 5. Włożenie bez zapasu i tak przesuwa cykl

**Plik:** `ReminderCenter.markInserted()`

`useNextRing()` przy braku krążków zwraca `false`; wynik jest ignorowany, cykl idzie dalej.

**Naprawa:** gdy zapas = 0, ostrzeżenie na ekranie potwierdzenia lub jawna zgoda „kontynuuj bez zapasu”.

### 6. Pasek postępu w przerwie liczy od daty włożenia, nie wyjęcia

**Plik:** `MainActivity.kt` (`HomeScreen`, gałąź `nextBreakEnd`)

Po wyjęciu `removeDate` jest ustawiane na `startDate` (ostatnie **włożenie**), nie dzień wyjęcia → zniekształcony łuk w przerwie.

**Naprawa:** zapisać `removed_on` w `SettingsStore` lub użyć daty z `recordRemoved`.

### 7. Kalendarz cyklu ignoruje potwierdzone przesunięcia

**Plik:** `MainActivity.kt` (`timeline`, `dayMark`, `CycleCalendarSheet`)

Kolory dni wynikają z matematyki od pierwotnego `startDate`, bez `current_insert` / `next_insert` → po wcześniejszym/późniejszym potwierdzeniu kalendarz **kłamie**.

**Naprawa:** oś oparta na rzeczywistych kotwicach z prefs albo oznaczenia „plan vs fakty”.

### 8. Miniony dzień wyjęcia/włożenia — brak ponownego alarmu

**Plik:** `ReminderCenter.kt` (`setAlarm`)

Alarmy tylko gdy data ≥ dziś. Po przeoczeniu jednego dnia użytkownik dostaje co najwyżej „po terminie” na widgecie, bez nowego przypomnienia.

**Naprawa:** jeśli dzień akcji minął i brak potwierdzenia → powiadomienie następnego ranka lub daily do potwierdzenia.

### 9. Drzemka (`snoozeUntilTomorrow`) nie przeplanowuje alarmu

**Plik:** `ReminderCenter.kt`

Tylko zapis w prefs; nie ma one-shot alarmu na jutro w tym samym sensie co wydarzenie cyklu.

**Naprawa:** przy drzemce zaplanować alarm lub `rescheduleFromSettings`.

### 10. Brak ekranu ustawień po kreatorze

**Pliki:** `SetupScreen`, `MainActivity`

Nie można zmienić tygodni cyklu, godziny przypomnień ani daty startu bez ukrytego `onClearSetup` (niewpiętego w UI). Użytkownik musiałby przeinstalować.

**Naprawa:** sekcja „Ustawienia” + ponowne `ReminderScheduler.schedule` po zapisie.

### 11. Release podpisany kluczem debug

**Plik:** `app/build.gradle.kts`

Release = debug signing → każdy z tym samym kluczem, zła ścieżka autoaktualizacji w środowisku produkcyjnym.

**Naprawa:** własny keystore w CI (poza gitem); dokumentacja w README już to ostrzega.

---

## Średnie

| # | Temat | Gdzie |
|---|--------|--------|
| 12 | W kreatorze drugi wybór godziny („Koniec”) nie jest zapisywany | `SetupScreen` |
| 13 | „Krążki starczą do” liczy `stock * (weeks*7+7)` od dziś, bez fazy cyklu | `StockSheet`, kalendarz |
| 14 | `PermissionStatusHint` nie odświeża się po powrocie z Ustawień systemowych | `MainActivity` (setup ma `LifecycleResumeEffect`, home nie) |
| 15 | Kamera skanera EAN: brak `unbindAll()` w `onDispose` | `StockSheet.BarcodeScannerBox` |
| 16 | Nagłówek zapasów max 12 kółek — zawyżenie przy dużym zapasie | `StockHeaderRow` |
| 17 | Sprawdzanie aktualizacji co **1 minutę** przy starcie | `AktualizacjaAplikacji.kt` — na produkcji 12–24 h |
| 18 | `AlarmReceiver` w manifeście, brak harmonogramu | martwy kod — usunąć lub podłączyć |
| 19 | Backup Android może przywrócić stare alarmy/stan | `backup_rules.xml` — wykluczyć krytyczne prefs |
| 20 | R8 wyłączone w release | większy APK; kiedyś włączyć z regułami dla receiverów |
| 21 | `clearSetup` nie czyści zapasu (`StockStore`) | świadoma decyzja lub pełny reset |

---

## Niskie

- Poprawna odmiana PL: krążek / krążki / krążków (`MainActivity`).
- `DayBeforeReceiver` bez sprawdzenia „już potwierdzono”.
- Prosta walidacja gestu X/ptaszek w `ReminderActivity` (fałszywe pozytywy).
- Testy jednostkowe prawie tylko dla `WidgetLogic` — brak regresji dla `StockStore`, `SettingsStore`, harmonogramu.
- Zależność DataStore w Gradle nieużywana.

---

## Co działa dobrze (v1.1 — bez zmian)

| Obszar | Stan |
|--------|------|
| Uprawnienia | Kanały, setup prowadzi do ustawień systemowych |
| Alarmy | `setAlarmClock` w dniu akcji, osobne `requestCode`, boot/replan |
| Powiadomienia | FGS bez sticky, kanały v2 |
| Widget | Północ + `refreshAll` po zdarzeniach |
| Git | Keystore/APK poza repozytorium |

**Czas / strefa:** manifest nasłuchuje `TIME_SET`; kod używa `Intent.ACTION_TIME_CHANGED` (to ten sam intent `android.intent.action.TIME_SET`) — **brak rozjazdu**.

**Po PR #1:** zużycie zapasu przy potwierdzeniu włożenia + odświeżenie paska zapasów po powrocie na home.

---

## Plan wdrożenia (propozycja)

1. **Sprint A (stabilność danych):** #1 stan cyklu w Compose, #2 `useRing`, #4 polityka zapasu, #3 reset + alarmy.
2. **Sprint B (zaufanie użytkownika):** #6–#8 przerwa/kalendarz/minione dni, #5 brak zapasu.
3. **Sprint C (produkt):** #10 ustawienia, #13 prognoza zapasu, #17 aktualizacje, #11 podpis release.

---

## Bezpieczeństwo git

Bez zmian: `local.properties`, `klucz`, `*.jks`, `*.apk`, `/app/release/` w `.gitignore`.