# Myring — Security / Privacy / Data Audit

**Scope:** `/workspace` (Android app `emilka.myring`, “Moje Krążki”)  
**Date:** 2026-11-21  
**Repo visibility:** **PUBLIC** (`NiewidzialnySI/Myring`)  
**Thoroughness:** very thorough (manifest, components, backup, signing, secrets, FileProvider, cleartext, WebView, logging, deep links, update supply chain, health-data privacy)

---

## Executive summary

| Severity | Count | Theme |
|----------|------:|-------|
| **P0** | 3 | Public release keystore + password; unsigned/unpinned auto-update install path |
| **P1** | 7 | Over-permissions, lockscreen health disclosure, plaintext reproductive prefs, update URL validation |
| **P2** | 8 | Backup gaps, exported widget action, R8 off, Play-policy permissions, docs contradiction |

**Highest risk chain:** leaked signing key (public git) → attacker can sign a replacement APK for `emilka.myring` → auto-update trusts remote `update.json` / `apkUrl` with **no APK signature or hash check** → remote code execution on user devices after one tap “Zaktualizuj”.

---

## Checklist results (requested areas)

| Area | Verdict |
|------|---------|
| AndroidManifest permissions | Several justified; **SYSTEM_ALERT_WINDOW** unused; dual exact-alarm + battery-opt + install-packages are aggressive |
| Exported components | Launcher + BootReceiver + AppWidget OK for role; custom `WIDGET_REFRESH` on exported widget is weakly gated |
| Backup rules | Prefs excluded (good intent); `allowBackup=true` + plaintext health prefs still weak; `aktualizacja_prefs` not excluded |
| Signing keystore in repo | **P0 — present and tracked** |
| Secrets in source | **P0 — store/key passwords in plaintext** |
| FileProvider paths | Narrow (`cache-path` → `apk/` only); exported=false — OK |
| Cleartext | No `usesCleartextTraffic` / no NSC (default block on API 31+); code still accepts `http://` URLs |
| WebView | **None found** — OK |
| Logging PII | **No `Log`/`println` in app sources** — OK |
| Deep links | **None** (no `BROWSABLE` / custom schemes) — OK |

---

## P0 — Critical

### P0-1. Release keystore committed to a public repository

**Files:**
- `signing/myring-release.jks` (tracked; `.gitignore` explicitly re-includes it)
- `.gitignore` lines 17–22 (`!signing/myring-release.jks`)
- `README.md` lines 22, 39
- `signing/README.md` lines 3–11, 23–25

**Why:** Anyone can download the PKCS12 and sign APKs as `emilka.myring`. Android will treat those APKs as legitimate updates of already-installed copies (same signing cert). SHA-256 fingerprint observed: `B5:40:9F:C6:0F:B9:BE:01:0D:FA:FF:23:B4:F0:30:00:64:70:4C:F9:F7:05:21:EF:7A:76:47:90:BB:6D:B9:9C`.

**Fix:** Rotate to a new keystore **never** committed; remove JKS from git history; distribute new installs only with the new key (existing installs cannot be updated without uninstall). Store secrets in CI/secret manager.

---

### P0-2. Release keystore passwords in plaintext in-repo

**Files:**
- `signing/signing.properties` lines 4, 6 (`storePassword` / `keyPassword`)
- `signing/README.md` lines 11, 33–34 (password documented in prose)
- `app/build.gradle.kts` lines 33–42 (loads those properties into `signingConfigs`)

**Why:** Completes key theft with zero cracking. Password is also duplicated in docs for convenience.

**Fix:** Remove passwords from git; use env vars / CI secrets; scrub history.

---

### P0-3. Auto-update installs APKs without integrity / signing verification

**Files:**
- `app/src/main/java/emilka/myring/update/AktualizacjaAplikacji.kt` lines 94–125 (fetch manifest), 140–156 (download APK), 167–215 (install via FileProvider), 248–256 (parse `apkUrl`), 302–309 (HTTP client, redirects on)
- `app/build.gradle.kts` lines 26–29 (`UPDATE_MANIFEST_URL` → public GitHub raw)
- `update.json` lines 1–6 (attacker-writable if repo compromised)
- `AndroidManifest.xml` line 20 (`REQUEST_INSTALL_PACKAGES`)

**Why:** Trust model is “whatever HTTPS JSON says.” There is no SHA-256/ pinned cert check of the APK, no verification that the APK is signed with the expected cert before launching the installer. Combined with P0-1/P0-2, a compromised `update.json` / release asset (or any host in `apkUrl`) yields a signed malicious update.

**Fix:** Embed expected signing cert digest; verify PackageInfo signatures after download; pin `apkUrl` host allowlist; prefer Play App Signing / in-app updates; remove sideload updater if possible.

---

## P1 — High

### P1-1. `apkUrl` validation claims HTTPS but allows `http://`

**File:** `AktualizacjaAplikacji.kt` line 253  
`require(apk.startsWith("http")) { "apkUrl musi być https" }`

**Why:** Message says HTTPS; check accepts cleartext. Default platform cleartext block (API 28+, here minSdk 31) mitigates today, but any future `usesCleartextTraffic=true` / NSC exception re-opens MITM of APK bytes.

**Fix:** Require `https://` only; reject redirects to non-HTTPS; optional certificate pinning for manifest host.

---

### P1-2. `SYSTEM_ALERT_WINDOW` declared and requested but never used for overlays

**Files:**
- `AndroidManifest.xml` line 8
- `SetupScreen.kt` lines 74, 143, 159, 190–207 (`canDrawOverlays`, settings intent)

**Why:** No `TYPE_APPLICATION_OVERLAY` / `WindowManager.addView` anywhere. Lockscreen UX uses `showWhenLocked` / `turnScreenOn` / full-screen intents (`ReminderActivity.kt` 39–41, `AndroidManifest.xml` 50–52). Overlay is a high-sensitivity permission with no functional need → over-permission + user trust cost.

**Fix:** Remove permission + setup step.

---

### P1-3. Lockscreen / public notifications disclose contraceptive use (sensitive health)

**Files:**
- `Notifications.kt` lines 50–51 (`setBypassDnd(true)`, `VISIBILITY_PUBLIC`)
- `Notifications.kt` lines 115–116 (“Jutro włóż/wyciągnij krążek”)
- `ReminderService.kt` lines 31–32, 52 (`VISIBILITY_PUBLIC`, explicit “krążek” copy)
- `ReminderCenter.kt` lines 115–116 (fallback titles)

**Why:** Reproductive-health reminders are visible on the lockscreen and can bypass DND. App name + wording reveal contraception to anyone near the device.

**Fix:** `VISIBILITY_PRIVATE` / secret; generic titles (“Przypomnienie Myring”); optional PIN/biometric gate for reminder content.

---

### P1-4. Sensitive cycle / stock data stored in plaintext SharedPreferences

**Files:**
- `SettingsStore.kt` lines 11–12, 26–33, 94–114 (cycle dates, insert/remove)
- `StockStore.kt` lines 22–24, 62–73 (EAN packages JSON)
- `ReminderCenter.kt` lines 25, 41–45 (snooze / action flags)
- `Notifications.kt` lines 159–168 (`myring_prefs`)

**Why:** Data is reproductive-health related. No `EncryptedSharedPreferences` / Keystore wrapping. Root, backup bugs, or forensic access reads cleartext.

**Fix:** Encrypt at rest; or `allowBackup="false"` + device credential; minimize retention.

---

### P1-5. `allowBackup="true"` for a health-tracking app

**Files:**
- `AndroidManifest.xml` lines 24–26
- `backup_rules.xml` lines 2–7
- `data_extraction_rules.xml` lines 3–15

**Why:** Excludes for `myring_*` prefs are good, but backup remains enabled; misconfigured paths or future prefs can leak. Stronger default for this data class is `allowBackup="false"` (and keep device-transfer excludes).

**Note:** `aktualizacja_prefs` is **not** excluded (`AktualizacjaAplikacji.kt` line 36) — lower sensitivity, but incomplete.

**Fix:** Prefer `android:allowBackup="false"`; if backup required, exclude all prefs/domains by default and include only non-sensitive keys.

---

### P1-6. Broad URI grants when installing update APK

**File:** `AktualizacjaAplikacji.kt` lines 193–207  
`queryIntentActivities` → `grantUriPermission` to **every** matching package, then `createChooser`.

**Why:** Any app advertising `VIEW` + APK MIME can receive a temporary read grant to the cached APK (malware dropper / APK theft). Prefer PackageInstaller session API targeting the system installer only.

**Fix:** Use `PackageInstaller`; do not chooser-grant to all handlers.

---

### P1-7. Aggressive privileged permissions for a personal cycle tracker

**File:** `AndroidManifest.xml` lines 6–7, 16, 20

| Permission | Concern |
|------------|---------|
| `USE_EXACT_ALARM` + `SCHEDULE_EXACT_ALARM` | Dual declare; `USE_EXACT_ALARM` is Play-restricted to alarm/calendar-like apps |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Play-restricted; requested directly in `SetupScreen.kt` 172–174 / `Notifications.kt` 90–92 |
| `REQUEST_INSTALL_PACKAGES` | Enables sideload update path (ties to P0-3) |

**Fix:** Drop unused/overbroad; for Play, use `SCHEDULE_EXACT_ALARM` with user grant only; avoid battery-opt exemption UX; move updates to Play.

---

## P2 — Medium / low

### P2-1. Exported AppWidget listens for custom refresh action

**Files:** `AndroidManifest.xml` lines 93–106; `MyringWidgetProvider.kt` lines 41–50, 55  
Action `emilka.myring.action.WIDGET_REFRESH` on `exported="true"` receiver.

**Impact:** Any app can spam refresh (DoS / battery). Prefer exported=false internal receiver for custom action; keep only `APPWIDGET_UPDATE` on the provider.

---

### P2-2. `BootReceiver` exported + `directBootAware`

**File:** `AndroidManifest.xml` lines 63–75; `BootReceiver.kt` lines 14–38  

Protected system broadcasts — spoof risk low on modern Android. `LOCKED_BOOT` path correctly avoids CE prefs. Keep as-is; optional `android:permission` not needed for protected actions.

---

### P2-3. R8 / optimization disabled on release

**File:** `app/build.gradle.kts` lines 50–54  

Larger, easier-to-reverse APK (health logic, update URLs). Enable minify with keep rules for receivers/Compose.

---

### P2-4. Home-screen widget exposes cycle phase in clear language

**Files:** `WidgetLogic.kt` lines 25–35 (“włóż/wyjmij krążek”); `MyringGlanceWidget.kt` 61, 155–163  

Visible to anyone who sees the launcher. Offer “discreet mode” labels.

---

### P2-5. `WAKE_LOCK` declared but unused in app code

**File:** `AndroidManifest.xml` line 15  

Likely redundant (AlarmManager / FGS). Remove if merge-manifest doesn’t need it.

---

### P2-6. Dead `manifest_url` override preference (no writer)

**File:** `AktualizacjaAplikacji.kt` lines 36–37, 65–69  

`K_URL` is read but never written in-repo. Future UI that writes it without allowlisting becomes a malicious-update vector. Remove or harden before exposing.

---

### P2-7. Documentation contradicts security posture

**Files:**
- `UPDATE.md` line 32 — “Nie commituj … keystore”
- `AUDYT.md` line 153 — “Keystore/APK poza repozytorium”
- vs `README.md` / `signing/README.md` / `.gitignore` exception

Misleads maintainers into thinking the key is safe.

---

### P2-8. Publish script embeds token in remote URL

**File:** `scripts/publish-github-release.sh` lines 8–14  

`GH_TOKEN` on argv/remote URL can leak via process list / shell history. Prefer `gh` auth / netrc; never echo remote.

---

## What looks solid

| Control | Evidence |
|---------|----------|
| No WebView / JS bridges | No matches in sources |
| No deep links / App Links | Manifest intent-filters are LAUNCHER + boot/widget only |
| No app `Log` / `println` PII | No `android.util.Log` usage in `app/src/main` |
| FileProvider locked down | `exported="false"`; paths only `cache-path` `apk/` (`file_paths.xml` 3–5; manifest 108–116) |
| Receivers mostly non-exported | `AlarmReceiver`, `ReminderReceiver`, `DayBeforeReceiver`, `WidgetRefreshReceiver`, `ReminderService`, `ReminderActivity` → `exported="false"` |
| PendingIntents immutable | Widespread `FLAG_IMMUTABLE` |
| Camera local-only | ML Kit on-device; no upload of frames/EAN (`EanBarcodeScanner.kt`) |
| No third-party analytics SDK | No Firebase/Crashlytics/etc. in Gradle |
| Backup excludes for core prefs | `backup_rules.xml` / `data_extraction_rules.xml` list `myring_settings|stock|reminders|prefs` |
| Default cleartext blocked | No `usesCleartextTraffic` / NSC; minSdk 31 |

---

## Permissions matrix (justification)

| Permission | Needed? | Notes |
|------------|---------|-------|
| `POST_NOTIFICATIONS` | Yes | Reminders |
| `SCHEDULE_EXACT_ALARM` | Yes | Cycle alarms |
| `USE_EXACT_ALARM` | Questionable | Play policy; prefer user-granted schedule only |
| `SYSTEM_ALERT_WINDOW` | **No** | Unused — remove |
| `RECEIVE_BOOT_COMPLETED` | Yes | Reschedule |
| `FOREGROUND_SERVICE` / `_SPECIAL_USE` | Yes | Full-screen reminder FGS |
| `USE_FULL_SCREEN_INTENT` | Yes | Alarm UX |
| `VIBRATE` | Yes | Alarm |
| `CAMERA` | Yes | EAN scanner |
| `WAKE_LOCK` | Likely no | No direct use |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Aggressive | OEM reliability vs Play policy |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Yes | Auto-update only |
| `REQUEST_INSTALL_PACKAGES` | Only if sideload updater kept | Prefer Play |

---

## Recommended remediation order

1. **Rotate signing key**; purge `signing/myring-release.jks` + passwords from git/history; revoke trust in old APKs (users reinstall).
2. **Disable or harden auto-update** (cert/hash verify, HTTPS-only, host allowlist, PackageInstaller).
3. Remove `SYSTEM_ALERT_WINDOW`; tighten notification visibility; encrypt or disable backup for health prefs.
4. Clean permission set for Play; enable R8; discreet widget/notification mode.

---

## Out of scope / not found

- Native code / JNI
- `google-services.json` / Firebase
- Hardcoded API tokens (other than keystore passwords)
- Deep link hijacking surface
- WebView XSS / file access
- Runtime logging of EAN/dates
