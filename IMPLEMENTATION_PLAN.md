# Implementation Plan — BP Diary (Android)

Audience: an AI coding agent (Claude Sonnet) implementing the app step by step.
Language of the app UI: **Russian**. Language of code, comments, commits: **English**.

Read this file fully before starting any step. It is the **highest-priority source**:
where it conflicts with `docs/` or `design_handoff_bp_diary/`, this file wins.

---

## 0. How to work

### 0.1 Source-of-truth order (highest first)

1. This file, section **1. Decisions** (mirrored in Russian in `docs/12-roadmap-backlog.md §12.7`).
2. `design_handoff_bp_diary/README.md` + `BPApp.dc.html` — visuals, sizes, colors, Russian micro-copy
   (except where a Decision overrides it, e.g. no user name in the greeting).
3. `docs/01…13` — requirements, architecture, rules, algorithms. **As of 2026-10-21 all errors from
   `docs/REVIEW-2026-10-21.md` and all customer answers are already applied in `docs/`**, so docs and this
   file agree. If you still find a conflict, stop and ask; do not pick one silently.

### 0.2 Golden rules

1. **One step per session.** Finish a step, make it pass its "Done when" checks, commit, stop, report.
   Do not start the next step in the same session unless the user tells you to.
2. **Never invent.** If a value, text, size or rule is not in the sources above, do not guess:
   stop and ask the user. List the question, your proposed default, and why.
3. **Never copy library versions from `docs/11`.** Look up the current stable versions
   (official release pages) at the time of the step. Put every version in `gradle/libs.versions.toml`.
   No hard-coded versions in `build.gradle.kts`.
4. **Russian UI strings only in `res/values/strings.xml` / `plurals`.** Copy Russian text from the
   handoff/docs **character-for-character** (including `«»`, `—`, `·`, non-breaking spaces in
   `мм рт. ст.`). Never translate or paraphrase them.
5. **Domain code is pure Kotlin** (`:core:model`, `:domain`): no `android.*` imports, no strings,
   no `System.currentTimeMillis()`, no `Instant.now()`, no `LocalDate.now()`. Time comes only from
   `TimeProvider` (Decision D-09).
6. **Tests first for logic.** For every function in `docs/06`, write the test table from the doc
   (corrected per REVIEW) before the implementation.
7. **Respect review-blocking rules** in `docs/02-architecture.md §2.9`. Re-read them before each UI step.
8. **Do not modify** `design_handoff_bp_diary/` or `docs/samples/`. Do not edit other `docs/` files
   unless the user asks.
9. **Build must stay green.** Before each commit run:
   `./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug`
   (add `verifyRoborazziDebug` once screenshot tests exist). Fix, don't suppress.
10. **Commit message format:** `step N.M: <short summary>`; one commit per step (more is fine, fewer is not).
11. Windows host: use the Gradle wrapper (`gradlew.bat` from PowerShell or `./gradlew` from Git Bash).

### 0.3 Step report template (end every session with this)

```
Step: N.M <name>
Status: done | blocked
Changed: <modules/files>
Checks run: <commands + result>
Deviations from docs: <none | list with reason>
Open questions: <none | list>
```

---

## 1. Decisions (override docs)

`CUSTOMER` = answered by the customer. `CONFIRM` = proposed default; implement it, but list it under
"Open questions" in the step report the first time you rely on it.

| ID | Decision | Status |
|---|---|---|
| D-01 | Released color direction: **SAGE** (Golos Text, number weight 700, card radius 14 dp). Keep PEACH and PLUM tokens in code; DataStore `direction` default = `SAGE`. No UI to switch direction in MVP. | CUSTOMER |
| D-02 | **No user name.** Greeting is always without a name: «Доброе утро» etc. Remove all name fields/settings. | CUSTOMER |
| D-03 | **No SQLCipher.** Plain Room. | CUSTOMER |
| D-04 | **No third-party SDKs at runtime** (no Crashlytics, no Firebase, no analytics, no Timber). **No `INTERNET` permission.** No crash-reporting opt-in screen. Logging: a tiny internal `Log` wrapper that is a no-op for anything containing measurement values in release. | CUSTOMER |
| D-05 | **PDF export is part of v1.1** (not v1.2). | CUSTOMER |
| D-06 | Critical-value hint: if SYS ≥ 180 **or** DIA ≥ 120 after save, show a dialog once per reading: text «Значение заметно выше ваших обычных уровней. Если самочувствие ухудшилось — обратитесь за медицинской помощью.», button «Понятно». No low-side hint. | CONFIRM |
| D-07 | Room schema **starts at version 1** with all columns, including `deleted_at`, `updated_at`, `created_at`, `tz_offset`. `pulse` is **nullable** (`INTEGER NULL`) — manual entry still requires it; null only comes from import. Null pulse is excluded from pulse averages and shown as empty. | CONFIRM |
| D-08 | Tags are stored as **stable keys**: `GOOD, HEADACHE, DIZZINESS, WEAKNESS, AFTER_MEDICINE, AFTER_EXERCISE` (in this display order). Russian labels (Хорошо, Головная боль, Головокружение, Слабость, После лекарства, После нагрузки) exist only in resources and in CSV export. | CONFIRM |
| D-09 | Time: interface `TimeProvider { fun now(): Instant; fun zone(): ZoneId }`. Production impl reads `ZoneId.systemDefault()` **on every call**. Do not inject a `java.time.Clock` singleton (it freezes the zone). Tests use `FixedTimeProvider(2026-10-20T09:12, Europe/Moscow)`. | CONFIRM |
| D-10 | MVP includes basic **edit** (tap row → Entry prefilled) and **delete** (swipe/menu → soft delete + snackbar «Отменить» 5 s; also «Удалить» on edit screen with a confirm dialog). Change history is v1.1. After saving an edit, `popBackStack()` to where the user came from; toast «Изменения сохранены». New reading → Home + toast «Измерение сохранено». | CONFIRM |
| D-11 | MVP includes a **minimal Settings** screen: theme (Системная / Светлая / Тёмная), «Удалить все данные», «О приложении» (disclaimer text, app version, privacy-policy URL as plain text/link). Entry point: a visible item at the bottom of the Levels screen, «Настройки», with `chevron_right`. Long-press on `tune` is **not** used. | CONFIRM |
| D-12 | Exact alarms: declare **only** `SCHEDULE_EXACT_ALARM` (no `USE_EXACT_ALARM`). If `canScheduleExactAlarms()` is false → `setWindow(RTC_WAKEUP, at, 10 min)` + banner on Reminders screen. Register a receiver for `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` → reschedule. | CONFIRM |
| D-13 | Snooze («Отложить на 15 минут») uses its **own request code** (`REQ_SNOOZE = 9000`) that `cancelAll()` never cancels. | CONFIRM |
| D-14 | Notification tap opens `MainActivity` via an **explicit** `PendingIntent` with extra `open=entry_new`. No public deep link / no exported intent-filter for it. | CONFIRM |
| D-15 | Fonts: bundle **Golos Text** 400/500/600/700 in `res/font` (OFL license file in `app/src/main/assets/licenses/`). No Downloadable Fonts. For PEACH/PLUM tokens keep the `FontFamily` reference but fall back to Golos Text (not bundled). | CONFIRM |
| D-16 | Backup: `allowBackup="true"` with `data_extraction_rules.xml` (API 31+) and `full_backup_content.xml` (API ≤30): **cloud backup excludes everything**, **device-to-device transfer includes** the Room DB and DataStore. Rationale: phone migration in MVP without any cloud copy. | CONFIRM |
| D-17 | Soft-deleted readings are purged when `deleted_at < now − 24 h` (daily WorkManager job + on app start). «Удалить все данные» and import «Заменить всё» hard-delete. | CONFIRM |
| D-18 | `disclaimer_ack` is an **Int** (accepted disclaimer version). Current version constant `DISCLAIMER_VERSION = 1`. Show the disclaimer if stored < current. | CONFIRM |
| D-19 | Home «7 дней» is computed in Kotlin from `observeRange` (no SQL aggregate in MVP). Bar color = `classify(avgSys: Double, avgDia: Double)` on **unrounded** daily means (as in the prototype). Header «среднее X/Y» = mean over **all readings** of the 7 days, rounded with `Math.round`. | CONFIRM |
| D-20 | Chart dots: a SYS dot is drawn in `hi` (diameter +1) when the **reading's status is HIGH** (either SYS or DIA over threshold) — as in the prototype. | CONFIRM |
| D-21 | Max/Min ties: pick the **latest** reading (docs/06.4, intentional deviation from prototype). | from docs |
| D-22 | Tag table migration (`tags`, `reading_tags`) is **v1.2**, not v1.1. | CONFIRM |
| D-23 | Text when all reminders are off on Home: «Напоминания выключены». | CONFIRM |
| D-24 | Week card when the last 7 days have no readings but older readings exist: show the card with the 7 day labels, no bars, and the text «За неделю измерений нет» instead of «среднее …». | CONFIRM |
| D-25 | Tests: JUnit 5 in pure JVM modules (`:core:model`, `:core:common`, `:domain`); JUnit 4 + Robolectric in Android modules (needed by Roborazzi). Fakes, not mocks. | CONFIRM |
| D-26 | Settings button «Удалить все данные» confirmation: two dialogs (second one with the red `hi` button «Удалить навсегда»). | CONFIRM |

---

## 2. Known traps (read before coding)

These were errors in `docs/` (now fixed there). They are listed because they are easy to get wrong
again from the prototype or from memory. Details in `docs/REVIEW-2026-10-21.md`.

1. **Keypad test table (`docs/06 §6.2`)**: input `1 2 8 9` → `sys="128"`, **`dia="9"`**, focus `DIA`.
   «128 82 71» takes **7** key presses, not 9.
2. `Reading` (domain) must carry `zoneOffset: ZoneOffset` — `localDay()` needs it.
3. `:data:repository` depends on `:domain` (it implements domain repository interfaces).
4. Demo data generator: **59** readings, not 60. Port it **literally** (see Step 1.6).
5. `06.6` example is wrong: a reminder that does not fire today is shown as `«Пн, 08:00 · …»` /
   `«Завтра, 08:00 · …»`, never as a bare `«08:00 · …»`.
6. `CsvReader` sketch in `docs/13` is incomplete (undefined `handle`, `flush`; empty lines; lone CR).
   Write your own state machine; test it with the fixtures.
7. CSV header mapping: `Метка времени` has priority; else combine `Дата` + `Время`; ignore `Статус`.
8. Arm detection from comment: whole-word whitelist only (see Step V1.4), not "root `прав`".
9. Fonts weights 500 and 600 are used by the handoff; include them.
10. Acceptance criterion "≤ 6 taps" is impossible; the real target is "≤ 2 taps besides digits".

---

## 3. Target module layout

```
bp-diary/
├── build-logic/convention/          # convention plugins (android-library, compose, jvm, hilt)
├── gradle/libs.versions.toml
├── app/                             # Application, MainActivity, NavHost, Hilt graph, theme wiring
├── core/model/                      # JVM: Reading, Thresholds, Reminder, Status, Arm, Tag, Field…
├── core/common/                     # JVM: TimeProvider, Result helpers, DemoData (testFixtures)
├── core/designsystem/               # Compose: BpColors, BpShapes, BpTypography, BpTheme, icons, fonts
├── core/ui/                         # Compose: BpCard, StatusChip, BigReading, NumPad, Stepper, ...
├── domain/                          # JVM: use cases, classify, keypad reducer, stats, geometry, formatter logic
├── data/database/                   # Room
├── data/datastore/                  # Preferences DataStore
├── data/repository/                 # repository impls (depends on :domain)
├── data/reminders/                  # AlarmScheduler, receivers, notification, workers
├── feature/{home,entry,diary,charts,reminders,levels,settings,onboarding}/
└── benchmark/                       # Macrobenchmark + Baseline Profile (Step 11)
```

`applicationId`: ask the user in Step 0.1 (propose `ru.<company>.bpdiary`).

---

## 4. MVP steps

Each step: **Goal / Read / Do / Done when**. Estimated size is for orientation only.

### Phase 0 — Project skeleton

#### Step 0.1 — Repository and Gradle skeleton
- **Read:** docs/02 §2.3, docs/11 §11.1–11.2, §11.7.
- **Do:**
  - Ask the user for `applicationId` and confirm git init. `git init`, `.gitignore`
    (Android + `keystore.properties` + `local.properties`).
  - Gradle wrapper (latest stable), version catalog with **current** stable versions (verify online).
    minSdk 26, compileSdk/targetSdk = latest stable API level that Play currently requires or higher.
  - `build-logic` convention plugins. All modules from §3 created (empty is fine) with correct
    dependencies.
  - Hilt wired in `:app`; empty `MainActivity` with `setContent { Text("ok") }`.
  - ktlint, detekt (with `ForbiddenImport` for `java.util.Date`, `ForbiddenMethodCall` for
    `System.currentTimeMillis`, `Instant.now`, `LocalDate.now`, `LocalDateTime.now`), Android Lint
    `warningsAsErrors`.
  - Room schema export dir `app/schemas` (or `data/database/schemas`) committed.
  - GitHub Actions workflow per docs/10 §10.9 (only the `check` job for now).
  - `CLAUDE.md` at the root: stack, module map, the rules from §0.2, link to this file.
- **Done when:** `./gradlew assembleDebug ktlintCheck detekt lintDebug` passes; app launches.

### Phase 1 — Pure domain (JVM only, no Android)

#### Step 1.1 — Core models
- **Read:** docs/03 §3.1, Decisions D-07, D-08, D-09.
- **Do:** `Arm`, `Status`, `Tag` (enum with the six keys, ordered), `Reading(id, takenAt: Instant,
  zoneOffset: ZoneOffset, sys, dia, pulse: Int?, arm, tags: Set<Tag>, comment, profileId)`,
  `Thresholds` (defaults 135/85/100/60/50/90), `Reminder(id, time: LocalTime, label, days: Set<DayOfWeek>, enabled)`,
  `Field { SYS, DIA, PULSE }`. `TimeProvider` + `SystemTimeProvider` + `FixedTimeProvider`.
- **Done when:** compiles; unit test for `Thresholds()` defaults.

#### Step 1.2 — Classification
- **Read:** docs/06 §6.1, D-19.
- **Do:** `classify(sys: Int, dia: Int, th)`, `classify(sys: Double, dia: Double, th)`,
  `pulseOutOfRange(pulse: Int?, th)` (null → false).
- **Done when:** parameterized test with the full table from §6.1 + threshold boundaries
  (`135/85`, `100/60`) + HIGH-over-LOW priority + a Double case `134.5/80 → NORMAL`.

#### Step 1.3 — Keypad reducer
- **Read:** docs/06 §6.2 (with the correction in §2 trap 1 above), handoff "Ввод с клавиатуры".
- **Do:** `Draft(sys, dia, pulse, field)`, `Key { Digit, Del, Next }`, `reduce(draft, key): DraftResult`
  (`Updated | Ignored | Save`). Also `focus(field)` for tapping a field.
- **Done when:** every row of the table passes, with the corrected row `1 2 8 9 → 128 / 9 / – , DIA`.
  Extra cases: `Del` on empty SYS does nothing; `Next` on PULSE returns `Save`; 4th digit in PULSE ignored.

#### Step 1.4 — Validation and save use case
- **Read:** docs/01 FR-1.5, docs/03 §3.7.
- **Do:** `ValidationRules` (SYS 50..300, DIA 30..200, PULSE 25..250, SYS > DIA, takenAt ≤ now),
  `ValidationError(field: Field?, reason: Reason)` where `Reason { SYS_RANGE, DIA_RANGE, PULSE_RANGE,
  SYS_NOT_GREATER, FUTURE_TIME }`. `SaveReadingUseCase` returns `SaveResult.Success(id) | Invalid(error)`.
- **Done when:** boundary tests (49/50/300/301 etc.), `sys == dia` invalid, future time invalid.

#### Step 1.5 — Dates and day grouping (no strings)
- **Read:** docs/06 §6.3, §6.6.
- **Do:** `localDay(reading)`, `DayLabel { Today, Yesterday, Date(LocalDate) }`,
  `greetingSlot(localTime) → MORNING(05–11:59) | DAY(12–17:59) | EVENING(18–22:59) | NIGHT(23–04:59)`.
  `Reminder.nextTriggerAfter(now: ZonedDateTime): ZonedDateTime?`, `nextReminder(list, now)`.
  `NextReminderLabel { Today(time, label), Tomorrow(time, label), Weekday(dow, time, label) }`.
- **Done when:** tests: day boundary at 23:59/00:00, reading with offset ≠ current zone,
  all 7 weekdays × {00:00, 07:59, 08:00, 23:59} × masks {daily, Mon–Fri, single day}, Friday 22:00 →
  Monday 08:00, DST change day (Europe/Berlin last Sunday of March) keeps wall-clock time.

#### Step 1.6 — Demo data generator (port)
- **Read:** `BPApp.dc.html` `makeData()` (lines ~345–358), docs/11 §11.2.
- **Do:** Port **literally** into `:core:common` `testFixtures` (and a debug-only copy for the app):
  - PRNG: `s = 11; rnd() = (s = s * 16807 % 2147483647) / 2147483647.0` using `Long`.
  - "now" = 2026-10-20 09:12 in `Europe/Moscow`.
  - Loop `d` from 29 down to 0, `h` in `[7, 21]`. Call order **exactly**:
    1) minute = `5 + floor(rnd()*40)`; build time; **if time > now → skip (rnd already consumed)**;
    2) `ev = if (h > 12) 5 else 0`, `tr = -(29 - d) * 0.3`;
    3) `sp = if (rnd() < 0.12) 14 else 0`; 4) `dp = if (rnd() < 0.06) -24 else 0`;
    5) sys = `round(134 + ev + tr + sp + dp + (rnd() - .5) * 16)`;
    6) dia = `round(86 + ev*.6 + tr*.5 + sp*.5 + dp*.6 + (rnd() - .5) * 10)`;
    7) pulse = `round(71 + (rnd() - .5) * 18)`; arm LEFT, no tags.
  - JS `Math.round` rounds half toward +∞. Java/Kotlin `Math.round(Double)` has the same semantics — use it.
    Never use `kotlin.math.round()` or `HALF_EVEN` (they round half to even).
- **Done when:** generator yields **59** readings; a test pins the first 3 and the last 3 readings.
  If `node` is available, extract `makeData()` to a scratch `.js` file, run it, and assert equality of
  all 59 rows. If not, ask the user to run the provided script and paste the output.

#### Step 1.7 — Period statistics
- **Read:** docs/06 §6.4, D-07 (null pulse), D-21.
- **Do:** `PeriodStats` and `computeStats(readings, th, period, now, zone)`.
  Period start = start of local day `today − (period − 1)`. Morning = local hour < 12.
  `inRangePercent = round(normal / total * 100)`.
- **Done when:** golden test on the demo data for 7 and 30 days stored in
  `domain/src/test/resources/stats_golden.json`. Generate expected values by an **independent** JS run of
  the prototype logic (same approach as Step 1.6) except Max/Min tie rule (D-21) — document any difference
  in a comment. Plus edge tests: 0, 1, 2 readings; no morning readings → morning = null.

#### Step 1.8 — Chart geometry
- **Read:** docs/06 §6.5, D-19, D-20.
- **Do:** `yLine`, `xLine`, `yBar`, `xBar`; `WeekBars` builder (7 days, today on the right, bars from
  max SYS to min DIA, status by unrounded means, missing days = no bar); `LineChartModel` builder
  (points by index, clipping flag if any value outside 50..180).
- **Done when:** tests from docs/10 §10.2 "Геометрия графиков" + week bars on demo data.

### Phase 2 — Data layer

#### Step 2.1 — Room
- **Read:** docs/03 §3.2–3.3, §3.6, D-07, D-17.
- **Do:** `BpDatabase` v1: `readings` (all columns, `pulse` nullable, `tags` as JSON array of keys),
  `reminders`. Indices as in docs. DAO from §3.3 + `observeById`, `getById`, `hardDeleteAll`,
  `purgeDeletedBefore`. Prefill 3 reminders in `onCreate` (labels: «Утреннее измерение»,
  «Дневное измерение», «Вечернее измерение»; masks 127/31/127; minutes 480/840/1260; enabled 1/0/1).
  `fallbackToDestructiveMigration` is forbidden. Add `room-paging`.
- **Done when:** Robolectric/instrumented DAO tests: insert/observe, range bounds inclusive, soft delete hides,
  restore, purge, paging filter for HIGH/NORMAL/LOW equals `classify()` on 500 random readings,
  prefill present on fresh DB. Schema JSON v1 committed.

#### Step 2.2 — DataStore
- **Read:** docs/03 §3.5, D-01, D-02, D-04, D-18.
- **Do:** keys: thresholds ×6, `last_arm`, `theme_mode`, `direction` (default SAGE), `font_scale_boost`,
  `onboarding_done`, `disclaimer_ack: Int`. No name key, no crash opt-in key.
  `ThresholdsRepository.observe()` with `distinctUntilChanged()`; writes validate invariants.
- **Done when:** tests: defaults, round-trip write/read, invariant violation rejected.

#### Step 2.3 — Repositories
- **Do:** `ReadingRepository`, `ThresholdsRepository`, `ReminderRepository`, `SettingsRepository`
  interfaces in `:domain`, impls in `:data:repository`, mappers Entity↔Domain, Hilt modules.
  Fake implementations in `testFixtures` for ViewModel tests.
- **Done when:** mapper tests (tags JSON ↔ `Set<Tag>`, offset ↔ `ZoneOffset`, null pulse).

### Phase 3 — Design system

#### Step 3.1 — Tokens and theme
- **Read:** docs/04 §4.1–4.2, handoff "Design Tokens", D-01, D-15.
- **Do:** `BpColors` (15 tokens, order `bg, sf, sf2, ink, mut, ac, aci, acs, hi, hiS, ok, okS, lo, loS, ln`),
  `Peach/Sage/Plum` × light/dark copied from the handoff hex list; `BpShapes(card, key = card*0.6)`;
  `BpTheme(direction, dark, content)` providing CompositionLocals and a derived `MaterialTheme`.
- **Done when:** a unit test asserts all 6 palettes have the exact 15 hex values from the handoff
  (parse the handoff README lines in the test or hard-code a copy and cross-check by eye in the report).

#### Step 3.2 — Typography, fonts, icons
- **Read:** docs/04 §4.3, §4.6, §4.7, handoff "Типографика", trap 9.
- **Do:** Golos Text 400/500/600/700 in `res/font`. `BpTypography` with all styles from docs/04 §4.3
  **plus** `unit` (14/500), `key` (24/600), `reminderTitle` (15/600), `navLabel` (12/700 active, 12/500 inactive).
  Numbers use weight 700 (SAGE). `numHero` letterSpacing −0.03em. Icons: export the 19 Material Symbols
  Rounded as vector drawables (`favorite` filled + outlined).
- **Done when:** previews render; no `dp` used for text sizes.

#### Step 3.3 — `core:ui` components
- **Read:** docs/04 §4.4–4.5, §4.8, handoff per-component specs.
- **Do:** `BpCard, StatusChip, BigReading, PulseRow, SegmentedToggle, FilterChipRow, Stepper, BpSwitch,
  NumPad, BpToast, DashedThresholdLine, BpTopBar, BpBottomNav`. All stateless. Accessibility semantics
  exactly as §4.8 («128 на 82 миллиметра ртутного столба», «цифра 7», «стереть», «далее», «сохранить»).
  Min touch target 48 dp. Previews: light/dark × 3 directions.
- **Done when:** a "showcase" debug screen shows all components; Roborazzi set up with first snapshots;
  contrast check: compute WCAG ratios for SAGE `ok/okS`, `hi/hiS`, `lo/loS`, `mut/bg`, `aci/ac` in a unit
  test and **report** any pair < 4.5 to the user (do not change colors yourself).

### Phase 4–8 — Screens

Common rules for every screen step:
- ViewModel exposes `StateFlow<XUiState>` + `Channel<XEffect>`; Composable `XScreen(state, onEvent)` is stateless.
- All formatting (dates, `128/82`, plurals) is done in a Presentation formatter, unit-tested.
- ViewModel tests with Turbine + fakes + `FixedTimeProvider`.
- Roborazzi snapshots: light + dark for SAGE (other directions only for Home and Entry), font scale 1.0 and 2.0.

#### Step 4 — Entry screen (`Route.Entry(readingId: Long?)`)
- **Read:** docs/05 §5.2, docs/06 §6.2, handoff §2, D-06, D-08, D-10.
- **Do:** keypad, auto-advance, field tap, live status (after ≥2 digits in SYS and DIA; hints «выше 135/85»,
  «ниже 100/60», «в пределах ваших уровней» with live thresholds), arm toggle (default `last_arm`),
  tags, comment (max 120, counter from 100), time chip (non-clickable in MVP), Save (disabled until 3 fields;
  validation error → `hi` outline + one line under the field grid, e.g. «Верхнее должно быть больше нижнего»
  — ask the user for the texts of the other `Reason`s), close/back with unsaved-draft dialog
  («Не сохранять измерение?» / «Не сохранять» / «Продолжить ввод»), edit mode (title «Измерение», prefilled,
  «Удалить» in the top bar), critical-value dialog (D-06). Draft survives rotation and process death
  (`SavedStateHandle`).
- **Done when:** VM tests for typing `128 82 71` + save, validation errors, edit flow; snapshots incl. error state.

#### Step 5 — Home (`Route.Home`)
- **Read:** docs/05 §5.1, handoff §1, D-02, D-19, D-23, D-24.
- **Do:** date header «Вторник, 20 октября», greeting without name, last-reading card (or empty card
  «Запишите первое измерение, и здесь появится ваша динамика»), main button, week card (Canvas,
  geometry from Step 1.8), next-reminder card, `tune` button → Levels. Midnight rollover: recompute on
  `ON_RESUME` and on `ACTION_DATE_CHANGED`/`ACTION_TIMEZONE_CHANGED` while visible.
- **Done when:** VM test "threshold change recolors last reading" (docs/10 §10.3); snapshots: empty, 1 reading,
  demo data, all reminders off.

#### Step 6 — Diary (`Route.Diary`)
- **Read:** docs/05 §5.3, docs/03 §3.3, docs/06 §6.7, D-10, D-17.
- **Do:** Paging 3 with SQL status filter; new `Pager` on filter/threshold change (`flatMapLatest`);
  day headers via `insertSeparators` (DayLabel → «Сегодня» / «Вчера» / «18 октября, воскресенье» — own
  month/weekday arrays, not `DateTimeFormatter`); row note «Повышенное · левая рука · Головная боль ·
  comment»; swipe-to-delete + snackbar «Измерение удалено» / «Отменить» (5 s) above the bottom bar;
  long-press menu (Изменить / Удалить / Копировать значения); empty states.
- **Done when:** formatter tests for headers and notes; paging test with 2 000 readings;
  deleting the last reading of a day removes its header.

#### Step 7 — Charts (`Route.Charts`)
- **Read:** docs/05 §5.4, docs/06 §6.4–6.5, handoff §4, D-20.
- **Do:** 7/30 toggle (`AnimatedContent`, 150 ms fade), Canvas line chart with uniform scale
  (height = width × 200/320; scale dash lengths too), legend, threshold dashes (SYS and DIA), x labels
  («14.10», «Сегодня»), clipping note «Значения выше 180 показаны по границе», stats grid 2×3 with plurals,
  in-range block (zero segments not drawn, no gap). < 2 readings → «Мало данных для графика. Нужно хотя бы
  два измерения», stats hidden. Chart `contentDescription` summary.
- **Done when:** VM tests vs golden stats; snapshots 7/30/not-enough-data.

#### Step 8 — Levels (`Route.Levels`)
- **Read:** docs/05 §5.6, docs/01 FR-5, D-11.
- **Do:** presets (change only high SYS/DIA; active when both match), 3 stepper groups with ranges
  (high SYS 110..200, high DIA 70..130, low SYS 70..120, low DIA 40..90, pulse 30..200, step 5), disabled
  (alpha .4) buttons that would break `low < high`, dark-theme switch, footnote, «Настройки» entry (D-11).
  DataStore write debounced 300 ms; UI updates immediately.
- **Done when:** VM tests for invariants and presets; E2E-style VM test: preset 140/90 → Home status changes.

### Phase 9 — Reminders

#### Step 9.1 — Scheduling core
- **Read:** docs/07 §7.1–7.3, D-12, D-13.
- **Do:** `ReminderScheduler.rescheduleAll()` (one exact alarm for the nearest enabled reminder;
  fallback `setWindow`), `ReminderReceiver` (show notification → reschedule), `BootReceiver`
  (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED`, `TIME_SET`, exact-alarm permission change)
  → enqueue `OneTimeWorkRequest` that calls `rescheduleAll()`. `ReminderWatchdogWorker` daily.
  Initial schedule after DB creation. Snooze with its own request code.
- **Done when:** Robolectric tests with `ShadowAlarmManager`: next alarm time for several `now`s; toggling;
  snooze survives `rescheduleAll()`; fallback path when exact alarms are denied.

#### Step 9.2 — Notification
- **Read:** docs/07 §7.4, D-14, docs/09 (no health data in notifications).
- **Do:** channel `reminders` («Напоминания об измерении», HIGH importance), title = reminder label,
  text «Пора измерить давление», tap → explicit intent to Entry, action «Отложить на 15 минут».
- **Done when:** Robolectric test of the built notification (no numbers in text).

#### Step 9.3 — Reminders screens
- **Read:** docs/05 §5.5, handoff §5.
- **Do:** list (sorted by time; disabled = alpha .5; switch reschedules), `ReminderEdit` (Material
  `TimePicker`, weekday chips Пн…Вс + presets «Ежедневно» / «Пн – Пт», label field, «Готово» disabled when no
  days, «Удалить напоминание» for existing), `DaysFormatter` («Ежедневно», «Пн – Пт», «Пн, Ср, Пт»),
  banners for missing `POST_NOTIFICATIONS` (2nd denial → app settings) and exact alarms.
- **Done when:** formatter tests; VM tests; snapshots with and without banners.

### Phase 10 — Shell, onboarding, settings

#### Step 10.1 — Navigation shell
- **Read:** docs/02 §2.4–2.5.
- **Do:** typed routes incl. `Onboarding`, `Disclaimer`, `Settings`; bottom nav only on 4 main tabs
  (`launchSingleTop`, `saveState/restoreState`); edge-to-edge + insets; splash kept until settings loaded
  (no theme flash); `font_scale_boost` multiplies system font scale; handle `open=entry_new` extra.
- **Done when:** UI test: tab switching keeps Diary scroll position; notification extra opens Entry.

#### Step 10.2 — Onboarding + disclaimer
- **Read:** docs/05 §5.8, docs/09 §9.6, D-02, D-04, D-12, D-18.
- **Do:** 3 screens (purpose; preset 135/85 or 140/90; reminders: enable morning/evening + request
  `POST_NOTIFICATIONS`, then explain and open exact-alarm settings if needed), then disclaimer screen with
  checkbox (text from docs/09 §9.6). No name step, no crash opt-in. Without acceptance the app does not proceed.
- **Done when:** VM tests; first launch → onboarding; second launch → Home.

#### Step 10.3 — Minimal settings
- **Read:** D-11, D-26, docs/09 §9.4, §9.6.
- **Do:** theme selector, «Удалить все данные» (hard delete DB rows, clear DataStore, cancel alarms,
  re-prefill default reminders, restart to onboarding), «О приложении» (full disclaimer +
  «При резком ухудшении самочувствия обратитесь за медицинской помощью», version, privacy URL — ask the
  user for the URL).
- **Done when:** instrumented test: after delete-all, DB file has 0 readings (incl. soft-deleted).

### Phase 11 — Quality

#### Step 11.1 — Screenshot matrix and accessibility
- Complete Roborazzi matrix (docs/10 §10.4, SAGE primary). `AccessibilityChecks.enable()` in UI tests.
  Manual TalkBack checklist from docs/10 §10.6 written into `docs/qa-checklist.md` for the user to run.

#### Step 11.2 — Performance
- `:benchmark` module: startup and diary scroll (2 000 demo readings) Macrobenchmarks, Baseline Profile
  for Home + Entry, committed. Report numbers vs docs/10 §10.7 thresholds.

#### Step 11.3 — E2E
- The three scenarios from docs/10 §10.5 (scenario 3 as a Robolectric test). CI `instrumented` job.

### Phase 12 — Release prep

- Release signing via `keystore.properties` (never commit secrets), R8 rules for serialization models only,
  `versionCode` from CI, `allowBackup` per D-16, manifest has **no** `INTERNET`.
- Write `docs/release-checklist.md` merging docs/09 §9.7 and docs/11 §11.5, adjusted for D-03/D-04/D-12.
  Include: Play Data Safety (no data collected/shared), Health apps declaration, privacy policy URL,
  closed testing 12+ testers × 14 days, target audience 18+.
- **Stop. Do not upload anything to Play Console.** That is the user's action.

---

## 5. v1.1 steps (after MVP is accepted)

| Step | Scope | Key references |
|---|---|---|
| V1.1 | Arbitrary date/time on Entry (date+time picker, no future) | docs/05 §5.2 |
| V1.2 | Edit history (`reading_history` table, migration 1→2 with `MigrationTestHelper` test) — **ask the user for the exact scope first** | docs/12 §12.2 |
| V1.3 | CSV export (canonical format, BOM, `;`, CRLF, `#` header, `Метка времени`), SAF save + share via `FileProvider` | docs/13 §13.3, `docs/samples/export-sample.csv` (byte-exact test) |
| V1.4 | CSV import: charset → dialect → RFC-4180 state machine → mapping (header synonyms with `Метка времени` priority; positional heuristic) → date parsing → validation → preview → merge strategy → single transaction in a Worker (copy the file to cache first). Arm whitelist: `левая, левой, левую, лев, правая, правой, правую, прав, left, right, л, п` as whole words, case-insensitive. Null pulse allowed (D-07). | docs/13 §13.2, §13.4–13.6; fixture `import-sample-ru-comma.csv` → 27 rows, 0 errors, 1 left arm detected |
| V1.5 | JSON backup export/import (streaming via `decodeToSequence` or manual array parsing; «Добавить» imports readings only, «Заменить всё» replaces everything) | docs/08 §8.3 |
| V1.6 | PDF report + print (D-05): header (period, thresholds — no name), chart via the same geometry, stats, table paginated after 50 rows | docs/08 §8.2 |
| V1.7 | Periods 90 days and «всё время» with SQL daily aggregate | docs/03 §3.4 |
| V1.8 | Full settings: font size boost, (direction switch only if the customer asks) | docs/05 §5.7 |
| V1.9 | Biometric lock (optional, off by default) | docs/09 §9.2 |
| V1.10 | «Напоминания не приходят?» help screen + hidden diagnostics (5 taps on version) | docs/07 §7.5 |

Round-trip test (export → import → identical) is mandatory once both V1.3 and V1.4 are done.

---

## 6. Questions to ask the user (collect, do not block unrelated steps)

1. `applicationId` and app display name. (Step 0.1)
2. Russian error texts for each validation `Reason` except «Верхнее должно быть больше нижнего». (Step 4)
3. Privacy-policy URL. (Step 10.3)
4. Confirm every `CONFIRM` decision in §1 (they are already implemented as proposed).
5. Contrast results from Step 3.3 if any pair is below 4.5:1 — designer decision.
