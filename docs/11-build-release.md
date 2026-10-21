# 11. Сборка и релиз

## 11.1 Стек и версии

> **Версии ниже — ориентир, а не требование.** Перед созданием проекта проверить актуальные
> stable-версии на официальных страницах релизов и зафиксировать их в version catalog.
> Не понижать версии ради совпадения с этой таблицей.

| Компонент | Версия (ориентир) | Примечание |
|---|---|---|
| Kotlin | актуальный stable 2.x | K2, Compose-плагин компилятора в составе Kotlin |
| AGP | актуальный stable | |
| Gradle | совместимый с AGP | configuration cache включён |
| JDK | 21 | toolchain, фиксируется в `gradle/libs.versions.toml` |
| Compose BOM | актуальный stable | Material 3 |
| minSdk / targetSdk / compileSdk | 26 / не ниже требования Play на момент релиза (≥ 36) / = targetSdk | minSdk 26 отсекает ~1% устройств и снимает кучу костылей (`java.time` через desugaring доступен и ниже, но Doze/каналы уведомлений проще) |
| Room | 2.7+ | KSP, + `room-paging` |
| Hilt | 2.5x | |
| Navigation Compose | 2.8+ | типизированные маршруты |
| DataStore | 1.1+ | Preferences |
| Paging | 3.3+ | дневник |
| kotlinx-serialization | 1.7+ | бэкап, маршруты |
| WorkManager | 2.9+ | сторож напоминаний, чистка корзины |

Все версии — только через version catalog (`gradle/libs.versions.toml`). Явные версии
в `build.gradle.kts` блокируют ревью.

```toml
[versions]
kotlin = "2.2.0"
agp = "8.13.0"
room = "2.7.1"

[libraries]
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx     = { module = "androidx.room:room-ktx",     version.ref = "room" }
room-compiler= { module = "androidx.room:room-compiler", version.ref = "room" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android      = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

Convention plugins в `build-logic/` — чтобы 12 модулей не копировали одинаковые блоки
`android { compileSdk = ... }`.

## 11.2 Конфигурации сборки

```kotlin
buildTypes {
    debug {
        applicationIdSuffix = ".debug"
        isDebuggable = true
        buildConfigField("boolean", "DEMO_DATA", "true")
    }
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        signingConfig = signingConfigs.getByName("release")
    }
    create("benchmark") {
        initWith(buildTypes.getByName("release"))
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += listOf("release")
        isDebuggable = false
        proguardFile("benchmark-rules.pro")
    }
}
```

**Демо-данные.** Генератор из прототипа (seed 11, 59 записей — см. [06.4](06-business-logic.md#эталонные-значения))
лежит в `testFixtures` модуля `:core:common` (для тестов) и в `src/debug` модуля `:app` (кнопка в debug-меню).
В release-сборку этот код не попадает вовсе (debug source set), `BuildConfig.DEMO_DATA` — дополнительная защита. Он же — датасет для скриншот-тестов
и golden-тестов статистики.

Флейворы для направлений **не делаем**: направление — настройка темы, а не вариант сборки.

## 11.3 Подпись

- Keystore в приватном репозитории/секретах CI, никогда в git.
- Play App Signing включён; upload key отдельный.
- Локально — `keystore.properties`, в `.gitignore`:

```kotlin
val props = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
signingConfigs.create("release") {
    storeFile = props["storeFile"]?.let { file(it) }
    storePassword = props["storePassword"] as String?
    keyAlias = props["keyAlias"] as String?
    keyPassword = props["keyPassword"] as String?
}
```

**Потеря upload key при включённом Play App Signing восстановима; потеря app signing key — нет.**
Бэкап keystore — обязательный пункт чек-листа первого релиза.

## 11.4 Версионирование

`versionName` — semver `1.0.0`. `versionCode` — монотонный integer из CI
(`число коммитов на main` либо дата `yyMMddHH`). Руками не правится.

Теги: `v1.0.0`. Релизные ветки не нужны при одной активной версии —
релиз собирается с тега на `main`.

## 11.5 Release-чек-лист

- [ ] `./gradlew clean check connectedDebugAndroidTest` — зелёный.
- [ ] Macrobenchmark: старт и скролл в пределах порогов ([10.7](10-testing.md#107-производительность)).
- [ ] Baseline Profile перегенерирован и закоммичен.
- [ ] Скриншот-эталоны обновлены и отревьюены дизайнером (не «approve all»).
- [ ] Проверка на устройствах: Android 8.0, 11, 14, 16; минимум один Xiaomi/Samsung
      с проверкой напоминаний после reboot.
- [ ] Чек-лист приватности ([09.7](09-privacy-security.md#97-чек-лист-перед-релизом)).
- [ ] Проверка `fontScale = 2.0` и TalkBack.
- [ ] Миграции с предыдущей версии: установить прошлый APK, наполнить данными, обновить, проверить.
- [ ] Размер bundle ≤ 12 МБ.
- [ ] `versionCode` больше предыдущего; changelog заполнен.

## 11.6 Play Console

### Листинг

- **Категория:** Health & Fitness (не Medical — Medical требует больше обоснований).
- **Короткое описание (80):** «Дневник артериального давления: записи, графики, напоминания.»
- **Полное описание:** первый абзац — что делает; второй — дисклеймер
  ([09.6](09-privacy-security.md#96-медицинские-дисклеймеры)); далее функции списком;
  в конце — «Все данные хранятся только на вашем телефоне».
- **Скриншоты:** 6 штук в направлении Шалфей, светлая тема + один тёмный; с реалистичными значениями,
  без «идеальных» 120/80 на всех. Приветствие без имени.
- **Data Safety:** заполняется по [09.4](09-privacy-security.md#94-google-play-data-safety).
- **Health apps declaration:** обязательная декларация для приложений о здоровье ([09.4](09-privacy-security.md#94-google-play-data-safety)).
- **Политика конфиденциальности:** обязательный URL.
- **Target audience:** 18+. Приложение не для детей — иначе включаются требования Families.

### Выпуск

1. Internal testing — своя команда, 1–2 дня.
2. Closed testing — 15–30 пользователей целевого возраста, **не меньше 14 дней**.
   Для личных аккаунтов разработчика, созданных после ноября 2023, Play требует closed testing
   с 12+ тестерами непрерывно 14 дней перед доступом к production — закладывать это в план заранее.
   Заодно напоминания отработают несколько циклов на разных прошивках.
3. Production — staged rollout 10% → 50% → 100% с интервалом 2–3 дня,
   наблюдение за crash-free rate (цель ≥ 99.5%) и ANR в Android vitals.

## 11.7 Структура репозитория

```
bp-diary/
├── build-logic/                    # convention plugins
├── gradle/libs.versions.toml
├── app/
│   ├── src/main/                   # Application, MainActivity, навигация, DI
│   ├── src/androidTest/            # E2E
│   └── schemas/                    # экспорт схем Room (в git!)
├── core/{model,common,designsystem,ui}/
├── data/{database,datastore,repository,reminders}/
├── domain/
├── feature/{home,entry,diary,charts,reminders,levels,settings,onboarding}/
├── benchmark/                      # Macrobenchmark + Baseline Profile
├── design_handoff_bp_diary/        # дизайн-референс (не компилируется)
└── docs/                           # эта документация
```

## 11.8 Пост-релиз

- Android vitals в Play Console (сторонних SDK нет) — разбор топ-5 крэшей и ANR в течение 48 часов.
- Отзывы в Play читаются раз в неделю; жалобы «напоминания не приходят» —
  в первую очередь проверять модель устройства и инструкцию из [07.5](07-reminders.md#75-производители-с-агрессивным-энергосбережением).
- Метрика успеха продукта (доля пользователей с ≥ 10 измерениями за первые 14 дней) **автоматически
  не собирается**: телеметрии нет, `INTERNET` нет. Оценивается по опросу closed-тестеров и отзывам.
