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
`./gradlew assembleDebug ktlintCheck detekt lintDebug testDebugUnitTest` (Windows: `gradlew.bat` or Git Bash `./gradlew`).
Needs `JAVA_HOME` (Temurin 21) and `ANDROID_HOME=D:\Android\Sdk` (no `local.properties`: lint rejects Windows paths there).
- ktlint: `.editorconfig` (max line 120, Composable naming exempt). Lint `warningsAsErrors`.
- detekt: `config/detekt/detekt.yml`; ForbiddenImport everywhere; ForbiddenMethodCall (time APIs) only in JVM modules (needs type resolution, wired into `detekt`). AGP 9 gives no detektDebug for Android modules.
- Backup: D-16 rules in `app/src/main/res/xml`. API<=30 `full_backup_content` cannot separate cloud/transfer, so it excludes all.

## Status
2026-10-21: Step 0.1 done (skeleton, empty app, CI `check` job). Next: Step 1.1.
