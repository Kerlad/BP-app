# Производительность: бенчмарки и Baseline Profile

Пороги из [10.7](10-testing.md#107-производительность). Модуль `:benchmark`, цель измерений: сборка `benchmark`
приложения (`ru.bpdiary.app.benchmark`, как release, подпись debug-ключом, ставится рядом с обычным приложением и не
трогает его данные).

Модуль `:benchmark` подключается к сборке только с флагом `-Pbp.benchmark` (в `settings.gradle.kts`): его настройка
`android.experimental.self-instrumenting` на AGP 9.5.0 роняет демон AAPT2 в остальных модулях при сборке ресурсов
юнит-тестов, поэтому обычная проверка (`./gradlew assembleDebug ... verifyRoborazziDebug`) идёт без него. Стиль и detekt
для него: `./gradlew -Pbp.benchmark :benchmark:ktlintCheck :benchmark:detekt`.

## Что измеряется

| Метрика | Порог | Тест |
|---|---|---|
| Холодный старт до первого кадра | < 700 мс (Pixel 4a) | `StartupBenchmark` (`StartupTimingMetric`) |
| Скролл дневника, 2 000 записей | 0 «медленных» кадров (> 16 мс) на 95-м перцентиле | `DiaryScrollBenchmark` (`FrameTimingMetric`) |
| Отрисовка графика за 30 дней | < 16 мс | вручную, трейс Perfetto (см. ниже) |
| Размер release-сборки | ≤ 12 МБ | отчёт в CI (шаг 12) |

Данные: перед каждым тестом `SeedReceiver` (есть только в сборке `benchmark`, `app/src/benchmark`) один раз заполняет базу
2 000 записей (`DemoData.bulk`, две в день, детерминированно) и пропускает онбординг и дисклеймер.

## Как запускать

Нужен **настоящий телефон** (желательно не слабее Pixel 4a), экран включён, отладка по USB.
Эмулятор подходит только для проверки, что тесты проходят: числа на нём ничего не говорят о телефоне, а
Macrobenchmark по умолчанию отказывается мерить на эмуляторе (для проверки добавить
`-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`).

```bash
./gradlew -Pbp.benchmark :benchmark:connectedBenchmarkAndroidTest
```

Отчёты: `benchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/<устройство>/` (JSON и
трейсы Perfetto). Смотреть:
- запуск: `timeToInitialDisplayMs` (медиана и максимум), сравнить `coldStartWithoutProfile` и `coldStartWithBaselineProfile`;
- скролл: `frameDurationCpuMs` P95 и `frameOverrunMs` P95 (отрицательное значение: кадр уложился в срок).

Результаты записать в таблицу ниже (дата, телефон, версия).

## Результаты

| Дата | Телефон | Запуск без профиля | Запуск с профилем | Скролл: P95 кадра | Комментарий |
|---|---|---|---|---|---|
| 2026-10-29 | Эмулятор Pixel 8 (AVD, Android 17) | 1103 мс | 896 мс | не мерили | Только проверка, что тесты проходят. Эти числа нельзя сравнивать с порогами. Профиль ускоряет запуск и на эмуляторе |
| не мерили | реальный телефон | — | — | — | Нужен, чтобы закрыть шаг по порогам |

## Baseline Profile

Файл `app/src/main/baseline-prof.txt` лежит в репозитории. Gradle кладёт его в APK, `profileinstaller` применяет его при
первом запуске. Профиль описывает стартовый путь: Главная, ввод измерения с клавиатурой, переключение между вкладками.

Обновить после заметных изменений стартового пути (новые экраны, смена навигации):

1. Подключить телефон или эмулятор (Android 9+).
2. Запустить генератор:

```bash
./gradlew -Pbp.benchmark :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ru.bpdiary.app.benchmark.BaselineProfileGenerator
```

3. Взять файл `*-baseline-prof.txt` из каталога отчётов (см. выше) и скопировать его содержимое в
   `app/src/main/baseline-prof.txt`.
4. Пересобрать, проверить `StartupBenchmark` с профилем и без, закоммитить.

Плагин `androidx.baselineprofile` не используется: он ещё не работает с AGP 9, поэтому профиль копируется вручную.

## Отрисовка графика (вручную)

Perfetto или «Профилировщик» Android Studio на сборке `benchmark`: открыть «Графики», переключить 7/30, найти кадр с
`Chart` в трейсе. Порог 16 мс. Автоматического теста нет: график рисуется один раз на Canvas без анимации.
