# BP Diary (Дневник давления)

Offline Android blood-pressure diary. User speaks Russian: reply in Russian; code, comments, commits in English; UI strings Russian (resources only).
Operating manual: `IMPLEMENTATION_PLAN.md` (highest priority; decisions D-01..D-26). Docs in `docs/` (Russian), design in `design_handoff_bp_diary/` (read-only).

## Stack
Kotlin, Compose + Material 3, Hilt, Room, DataStore, Paging 3, WorkManager. minSdk 26, compile/target SDK 37, JDK 21, AGP 9 (built-in Kotlin), KSP.
applicationId `ru.bpdiary.app` (debug: `.debug`). Versions only in `gradle/libs.versions.toml`; verify latest stable online before bumping.

## Modules (plan §3; convention plugins in `build-logic/convention`)
| Module | Type | Plugin ids |
|---|---|---|
| `:app` | application, Hilt, NavHost | `bp.android.application/compose/hilt` |
| `:core:model`, `:core:common`, `:domain` | pure Kotlin JVM, JUnit 5 | `bp.jvm` |
| `:core:designsystem`, `:core:ui`, `:feature:*` | Android lib + Compose | `bp.android.library/compose` |
| `:data:database` | Android lib + Room (schemas in `data/database/schemas`, commit them) | `bp.android.room` |
| `:data:datastore`, `:data:repository`, `:data:reminders` | Android lib | `bp.android.library` |
Deps: `:data:repository` -> `:domain`; features -> `:core:ui`, `:domain`. `:benchmark` comes in Step 11.

## Golden rules (plan §0.2)
1. One plan step per session; stop and report (template plan §0.3, in Russian).
2. Never invent values/texts: ask. Russian strings only in `res/values`, copied char-for-char.
3. `:core:model`/`:domain`: no `android.*`, no strings, no `now()` calls; time only via `TimeProvider` (D-09).
4. Tests first for logic. Fakes, not mocks.
5. Do not edit `design_handoff_bp_diary/`, `docs/samples/`, other `docs/` unless asked.
6. No `INTERNET` permission, no third-party runtime SDKs, no SQLCipher.
7. Commit: `step N.M: summary`. Never commit `keystore.properties`/`local.properties`.

## Verify
`./gradlew assembleDebug ktlintCheck detekt lintDebug test testDebugUnitTest verifyRoborazziDebug` (`test` runs JVM modules; `testDebugUnitTest` does not; screenshots: `recordRoborazziDebug` re-records them) (Windows: `gradlew.bat` or Git Bash `./gradlew`).
Needs `JAVA_HOME` (Temurin 21) and `ANDROID_HOME=D:\Android\Sdk` (no `local.properties`: lint rejects Windows paths there).
- Lint: network-dependent version checks (AndroidGradlePluginVersion, GradleDependency, NewerVersionAvailable) are disabled so online and offline builds agree.
- ktlint: `.editorconfig` (max line 120, Composable naming exempt). Lint `warningsAsErrors`.
- detekt: `config/detekt/detekt.yml`; ForbiddenImport everywhere; ForbiddenMethodCall (time APIs) only in JVM modules (needs type resolution, wired into `detekt`). AGP 9 gives no detektDebug for Android modules.
- Backup: D-16 rules in `app/src/main/res/xml`. API<=30 `full_backup_content` cannot separate cloud/transfer, so it excludes all.

## Status
2026-10-29: Phases 1-3 done; Steps 4-8 (Entry, Home, Diary, Charts, Levels) 9.1 (reminder scheduling), 9.2 (notification), 9.3 (Reminders screens), 10.1 (shell), 10.2 (onboarding + disclaimer) done. Next: Step 10.3 (settings). No-op callback in app/BpApp.kt: Levels → Settings (10.3).
Notes: Robolectric needs --add-opens (set in bp.android.library). Gradle needs dl.google.com (use VPN/hotspot to download, then --offline); a new module or test dependency often pulls old AndroidX that must be fetched once online. DataStore file store cannot be used in JVM tests on Windows (renameTo): use InMemoryPreferencesStore. detekt: MagicNumber excluded in Palettes.kt/BpTypography.kt, FunctionNaming and LongParameterList ignore @Composable, UnusedPrivateMember ignores @Preview. Launcher icon from app/icon-src (make_icon.py). Library R class lives in the module namespace ru.bpdiary.app.<module>. Roborazzi comes from the bp.android.screenshots convention plugin; snapshots are in core/ui/src/test/snapshots (recorded on Windows, not yet in CI). Compose UI tests need espresso-core 3.7.0 (older fails on the newest SDK). Component showcase: debug only, `ru.bpdiary.app.debug.ShowcaseActivity`. Contrast report: core/designsystem/build/reports/contrast.txt (16 pairs below 4.5 in light themes, awaiting the designer).
Screens: type-safe routes via `bp.android.navigation` (serialization plugin + navigation-compose); feature exposes `XRoute` + `NavGraphBuilder.xScreen()`; VM args via Hilt assisted inject + `hiltViewModel(creationCallback)` from hilt-lifecycle-viewmodel-compose. App-wide toast/undo snackbar/D-06 dialog live in app/BpApp.kt (shell comes in 10.1). Shared formatting in core:ui (`BpDateFormatter`, `Tag.labelRes()`).
PROVISIONAL strings (marked in strings.xml, await the customer): entry: 5 validation errors, delete dialog, «Закрыть»; home: `tune` description, week chart description; diary: copied text, swipe label, pulse description; charts: chart description; levels: back and stepper descriptions.
Diary: `ReadingRepository.paged(status, th)` (Room PagingSource, SQL filter); VM re-creates the pager on filter/threshold change, headers via insertSeparators + `dayHeaderBetween`. Static `PagingData.from` in fakes/tests needs explicit NotLoading load states, otherwise `asSnapshot` hangs and empty states never show. Shared UI: `BpPrimaryButton`, `DayChangeEffect` (ON_RESUME + date/time/zone broadcasts) in core:ui.
Reminders: domain `ReminderScheduler`/`ReminderNotifier`; data:reminders `AlarmReminderScheduler` (REQ_REMINDER 1000, REQ_SNOOZE 9000), `ReminderReceiver`, `BootReceiver` → `RescheduleWorker`, daily `ReminderWatchdogWorker`; `BpDiaryApplication` is the WorkManager Configuration.Provider (startup initializer removed) and calls `ReminderWork.start`. Library manifests need fully qualified class names (namespace differs from package). Theme: MainActivity applies `themeMode` from ShellViewModel (Levels switch sets DARK/LIGHT). Threshold steps/presets in domain `ThresholdsEditing.kt` (null = disabled). Locale: MainActivity forces `ru` (plurals follow the configuration locale), bundle language splits off; Robolectric tests that check plurals need the `ru` qualifier. Roborazzi: a short screen was sometimes captured blank; screen tests invalidate the root view before capture. Screens (9.3): `RemindersRoute`/`ReminderEditRoute(id?)`; VMs call `ReminderScheduler.rescheduleAll()` after every save/delete/switch; domain `ReminderPermissions` (data:reminders `AndroidReminderPermissions`) drives the banners, refreshed on ON_RESUME; after a 2nd denial the button opens app settings (decided from `shouldShowRequestPermissionRationale`, not persisted). `daysSummary()` + `RemindersTexts` give «Ежедневно»/«Пн – Пт»/«Пн, Ср, Пт». `BpPrimaryButton(enabled)`. PROVISIONAL strings: banner buttons, edit titles, field/day-group names, default label «Измерение давления», delete dialog, descriptions. M3 `TimePicker` is experimental (opt-in); at font scale 2 its dial digits overlap (Material's own).
Shell (10.1): `AppShell` (bottom nav only when `AppTab` matches the destination; nav-bar inset consumed above it, toast/snackbar as overlay), `navigateToTab` (saveState/restoreState, launchSingleTop), `openNewEntry` (no second Entry). MainActivity: SplashScreen held until `ShellViewModel.appearance` (theme + font boost) is read; `BoostedFontScale` multiplies the system font scale; `open=entry_new` handled only on fresh launch/onNewIntent, not after LAUNCHED_FROM_HISTORY. Launch theme `Theme.BpDiary.Starting`, splash colours = Sage `bg` (values/-night). Settings route comes with its screen in 10.3. App tests: Robolectric (`--add-opens` now also in bp.android.application). Onboarding (10.2): `OnboardingRoute` (3 steps in one VM: purpose, levels preset, reminder switches + permission hints that never block) then `DisclaimerRoute` (checkbox, stores `DISCLAIMER_VERSION`); choices are written at once; `onboardingDone` is set when the last step is left. Domain `launchGate(onboardingDone, ack)` picks the start screen, `ShellViewModel.launchGate` holds the splash until read; `openNewEntry` ignores notifications while Onboarding/Disclaimer is on top; `openAppAfterDisclaimer` makes Home the stack bottom. Shared helpers in core:ui `SystemSettings.kt` (notification/exact-alarm settings, `POST_NOTIFICATIONS_PERMISSION`). PROVISIONAL strings: purpose and reminders paragraphs, «Позже уровни можно изменить.», «Далее», «Открыть настройки»/«Настроить», disclaimer title «Важно», checkbox «Я прочитал(а) и понимаю», «Продолжить», step description.
