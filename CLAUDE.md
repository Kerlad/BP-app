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
- ktlint: `.editorconfig` (max line 120, Composable naming exempt). Lint `warningsAsErrors`.
- detekt: `config/detekt/detekt.yml`; ForbiddenImport everywhere; ForbiddenMethodCall (time APIs) only in JVM modules (needs type resolution, wired into `detekt`). AGP 9 gives no detektDebug for Android modules.
- Backup: D-16 rules in `app/src/main/res/xml`. API<=30 `full_backup_content` cannot separate cloud/transfer, so it excludes all.

## Status
2026-10-28: Phases 1-3 done; Step 4 (Entry) and Step 5 (Home, :feature:home) done. Next: Step 6 (Diary). Home callbacks for Levels/Charts/Reminders are no-ops in app/BpApp.kt until Steps 7, 8, 9.3.
Notes: Robolectric needs --add-opens (set in bp.android.library). Gradle needs dl.google.com (use VPN/hotspot to download, then --offline); a new module or test dependency often pulls old AndroidX that must be fetched once online. DataStore file store cannot be used in JVM tests on Windows (renameTo): use InMemoryPreferencesStore. detekt: MagicNumber excluded in Palettes.kt/BpTypography.kt, FunctionNaming and LongParameterList ignore @Composable, UnusedPrivateMember ignores @Preview. Launcher icon from app/icon-src (make_icon.py). Library R class lives in the module namespace ru.bpdiary.app.<module>. Roborazzi comes from the bp.android.screenshots convention plugin; snapshots are in core/ui/src/test/snapshots (recorded on Windows, not yet in CI). Compose UI tests need espresso-core 3.7.0 (older fails on the newest SDK). Component showcase: debug only, `ru.bpdiary.app.debug.ShowcaseActivity`. Contrast report: core/designsystem/build/reports/contrast.txt (16 pairs below 4.5 in light themes, awaiting the designer).
Screens: type-safe routes via `bp.android.navigation` (serialization plugin + navigation-compose); feature exposes `XRoute` + `NavGraphBuilder.xScreen()`; VM args via Hilt assisted inject + `hiltViewModel(creationCallback)` from hilt-lifecycle-viewmodel-compose. App-wide toast/undo snackbar/D-06 dialog live in app/BpApp.kt (shell comes in 10.1). Shared formatting in core:ui (`BpDateFormatter`, `Tag.labelRes()`).
PROVISIONAL strings (marked in strings.xml, await the customer): entry: 5 validation errors, delete dialog, «Закрыть»; home: `tune` description, week chart description.
Home refreshes "now" on ON_RESUME + DATE/TIME/TIMEZONE_CHANGED (HomeViewModel.refresh). Roborazzi: a short screen was sometimes captured blank; HomeScreenTest invalidates the root view before capture.
