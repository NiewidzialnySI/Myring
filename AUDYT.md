# Audyt pełny Myring — wszystkie funkcje (2026-11-21)

Audyt równoległy **osobnymi agentami** na każdy obszar aplikacji (nie tylko powiadomienia), potem wdrożenie P0/P1 na gałęzi `cursor/audit-fix-notifications-ec66`.

## Obszary (agenci)

| # | Obszar | Zakres |
|---|--------|--------|
| 1 | Powiadomienia / alarmy | AlarmManager, FGS, FSI, Boot, snooze, catch-up |
| 2 | Cykl / Home | `computeCycle`, faza, CTA, zaległość |
| 3 | Kalendarz | `dayMark`, INSERT/BREAK, timeline |
| 4 | Zapas / prepaid | `StockStore`, double debit, `hasStockForInsert` |
| 5 | EAN / kamera | CameraX, latch skanu, permission |
| 6 | Setup / uprawnienia | DatePicker UTC, tick, FSI API 34 |
| 7 | Ustawienia | `saveReminderTime` bez nadpisu `start_date` |
| 8 | Widget / motyw | Glance, AMOLED, midnight reschedule |
| 9 | Update / bezpieczeństwo | `apkUrl` tylko https, keystore |

---

## Wdrożone w tym PR

### Powiadomienia
- Catch-up +2 min gdy godzina dziś już minęła / zaległość
- FGS zawsze otwiera `ReminderActivity`
- Setup: `areNotificationsEnabled` (API 31–32)
- `onResume` + północ → `ReminderScheduler.rescheduleFromSettings`
- Snooze: nie planuj dziennych alarmów na dziś (unik pętli +2 min)
- FSI: `canUseFullScreenIntent` + kafelek w Setup + hint w Ustawieniach (API 34+)

### Cykl / Home
- `RING_ON` do potwierdzenia wyjęcia (nie skaczemy w BREAK „na papierze”)
- CTA zaległe (`ActionDayButton`) dla remove/insert
- UI zaległości: „X dni po terminie / wyjęcie zaległe”
- `dayMark`: dzień 0 = INSERT, dzień wyjęcia = BREAK (kalendarz + `CycleMarking`)

### Zapas / EAN
- `hasStockForInsert` respektuje prepaid
- `markInserted` nie debituje drugi raz przy prepaid
- Latch skanera EAN (jedno dostarczenie kodu)
- Skaner montowany dopiero po `CAMERA` grant

### Setup / ustawienia / update
- `SettingsStore.saveReminderTime` — tylko godzina, bez `start_date`
- DatePicker w UTC (bez przesunięcia strefy)
- Tick odświeża kafle uprawnień po powrocie z systemu
- `apkUrl` wymaga `https://`

### Widget / system
- Glance motyw z `ThemePreference`
- `goAsync` przy refresh
- `data_extraction_rules` wyklucza prefs
- `AppReset` anuluje alarmy

---

## Follow-up po audycie Setup/ustawienia

Z raportu [Audyt Setup i ustawień](bc-0eef1f69-f423-51dd-a8dc-4e800961ea34):
- `AppReset` czyści też `myring_prefs`, gasi FGS/notyfikacje
- Settings: `LifecycleResumeEffect` odświeża hinty FSI/exact
- Demo `ReminderActivity` nie stopuje prawdziwego alarmu; `onNewIntent` dla `singleTask`
- Update: HTTPS także dla manifeście + redirect tylko po https

## Pozostałe (P2 / świadomie otwarte)

| Priorytet | Temat | Uwaga |
|-----------|--------|--------|
| P1 | Re-nag alarmu co 15/60 min do potwierdzenia | Opcjonalne; catch-up + CTA na Home pokrywa minimum |
| P1 | Kalendarz vs `current_insert` / `next_insert` | Timeline nadal od kotwicy matematycznej; po wczesnym/późnym potwierdzeniu kolory mogą się rozjechać |
| P2 | OEM `QUICKBOOT_POWERON` | Zależne od producenta |
| P1 | Publiczny keystore w repo | Ryzyko podpisów — osobna decyzja release (nie ruszane w tym PR) |
| P2 | `useRing` legacy API | Nie używane w ścieżce UI; zostawić lub usunąć |
| P2 | Custom godzina tylko chipy 8/12/18/20 w Settings | Setup pozwala inną — Settings nie edytuje |

---

## Werdykt

Aplikacja była audytowana **pod każdym głównym obszarem funkcji**, nie tylko powiadomieniami. Krytyczne P0 z tego przebiegu są wdrożone w tym PR. Pozostałe pozycje to świadomy backlog (P1/P2).
