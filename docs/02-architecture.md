# 02. Архитектура

## 2.1 Принципы

1. **Однонаправленный поток данных.** UI наблюдает `StateFlow<UiState>` и отправляет события в ViewModel.
   ViewModel не знает о Compose, Compose не знает о Room.
2. **Единый источник истины — база.** Экраны не кэшируют списки в собственных полях; всё через `Flow` из DAO.
3. **Статус измерения не хранится.** Он вычисляется из измерения и текущих порогов. Иначе смена порогов
   потребует миграции данных и даст рассинхрон (см. FR-5.4 в [01](01-requirements.md)).
4. **Домен чист.** Модуль `core/model` и use-case'ы не зависят от Android SDK — тестируются на JVM.
5. **Время инжектится.** Нигде в логике нет `System.currentTimeMillis()`, `Instant.now()`,
   `LocalDate.now()`: только `TimeProvider` (см. [2.6](#26-di)).
   Прототип фиксирует «сейчас» на 20.10.2026 09:12 — тесты должны уметь то же самое.

## 2.2 Слои

```
+---------------------------------------------+
| UI (Compose)                                |
|  HomeScreen, EntryScreen, DiaryScreen, ...  |
|  stateless: UiState + (Event) -> Unit       |
+---------------------------------------------+
| Presentation                                |
|  HomeViewModel ... : StateFlow<UiState>     |
|  маппинг домен -> UiState (готовые строки)  |
+---------------------------------------------+
| Domain                                      |
|  модели: Reading, Thresholds, Reminder      |
|  use-case: SaveReading, ClassifyReading,    |
|            GetPeriodStats, ScheduleReminder |
|  интерфейсы репозиториев                    |
+---------------------------------------------+
| Data                                        |
|  Room + DAO | DataStore | ReminderScheduler |
|  реализации репозиториев                    |
+---------------------------------------------+
```

**Форматирование — в Presentation.** `UiState` отдаёт в Compose готовые `"128/82"`, `"Сегодня, 07:24"`,
`"Повышенное"`. Compose не форматирует даты и не склоняет слова: это нужно тестировать, а тесты
на ViewModel дешевле тестов на Compose.

Домен строк не возвращает: он отдаёт значения (`Status`, `DayLabel.Today/Yesterday/Date`,
`GreetingSlot`), а русские тексты из ресурсов подставляет Presentation.

## 2.3 Модули Gradle

| Модуль | Тип | Зависит от |
|---|---|---|
| `:app` | application | все feature, core, data |
| `:core:model` | kotlin jvm | — |
| `:core:common` | kotlin jvm | `:core:model` |
| `:core:designsystem` | android-library + compose | — |
| `:core:ui` | android-library + compose | designsystem, model (общие composable: StatusChip, Stepper, BigNumber) |
| `:data:database` | android-library | `:core:model` |
| `:data:datastore` | android-library | `:core:model` |
| `:data:repository` | android-library | database, datastore, `:domain`, `:core:common` (реализует интерфейсы репозиториев из `:domain`) |
| `:data:reminders` | android-library | `:domain`, `:core:common` (`ReminderScheduler`, ресиверы, уведомления, воркеры) |
| `:domain` | kotlin jvm | `:core:model`, `:core:common` |
| `:feature:home`, `:feature:entry`, `:feature:diary`, `:feature:charts`, `:feature:reminders`, `:feature:levels`, `:feature:settings`, `:feature:onboarding` | android-library + compose | `:core:ui`, `:domain` |

Монолитный `:app` допустим на старте, но границы пакетов держать те же: выделение модулей
потом станет механическим.

## 2.4 Навигация

Navigation Compose, типизированные маршруты (Kotlin Serialization).

```kotlin
@Serializable sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data object Diary : Route
    @Serializable data object Charts : Route
    @Serializable data object Reminders : Route
    @Serializable data class  Entry(val readingId: Long? = null) : Route  // null = новая запись
    @Serializable data object Levels : Route
    @Serializable data class  ReminderEdit(val id: Long? = null) : Route
    @Serializable data object Settings : Route
    @Serializable data object About : Route
    @Serializable data object Onboarding : Route
    @Serializable data object Disclaimer : Route
    // v1.1: Import, Export, Backup, ReminderHelp, ReminderDiagnostics
}
```

- Нижняя навигация показывается только на `Home | Diary | Charts | Reminders` — это и есть
  «основные» экраны из хендоффа.
- `Entry`, `Levels`, `ReminderEdit`, `Settings`, `About`, `Onboarding`, `Disclaimer` — полноэкранные,
  навигация скрыта, фон жестовой полосы `bg` (а не `sf2`).
- Переключение таба — `launchSingleTop` + `popUpTo(startDestination)` с `saveState/restoreState`:
  позиция скролла дневника сохраняется.
- Карточка «7 дней» на Главной ведёт на `Charts`; карточка напоминания — на `Reminders`;
  круглая кнопка `tune` — на `Levels`; пункт «Настройки» внизу «Уровней» — на `Settings`.
- Из уведомления: **явный** `PendingIntent` на `MainActivity` с extra `open=entry_new` → `Route.Entry(null)`.
  Публичный deep link (`intent-filter` со схемой) не объявляется — иначе экран ввода может открыть
  любое приложение.
- После сохранения новой записи `Entry` → `Home`; после сохранения правки — `popBackStack()`.

## 2.5 Хранение состояния

| Что | Где | Почему |
|---|---|---|
| Измерения, пороги, напоминания | Room / DataStore → `Flow` | источник истины |
| Фильтр дневника, период графика | `SavedStateHandle` | пережить смерть процесса |
| Черновик ввода (sys/dia/pulse/field/tags/comment) | `SavedStateHandle` экрана ввода | потеря ввода при повороте недопустима |
| Последняя выбранная рука | DataStore | по хендоффу подставляется в следующий раз |
| Тема, направление, масштаб шрифта | DataStore, читается в `:app` до первого кадра | иначе мигание темы при старте |

Чтобы тема не мигала, `MainActivity` держит сплэш через `SplashScreen.setKeepOnScreenCondition`,
пока настройки не прочитаны.

## 2.6 DI

Hilt, по одному `@Module` на слой:

```kotlin
@Module @InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun db(@ApplicationContext c: Context): BpDatabase =
        Room.databaseBuilder(c, BpDatabase::class.java, "bp.db")
            .addMigrations(*BpDatabase.MIGRATIONS)
            .build()

    @Provides fun readingDao(db: BpDatabase) = db.readingDao()

    @Provides @Singleton fun time(): TimeProvider = SystemTimeProvider
}

interface TimeProvider {                 // :core:common
    fun now(): Instant
    fun zone(): ZoneId
}

object SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()   // читается при КАЖДОМ вызове
}
```

**Не использовать** `Clock.systemDefaultZone()` как синглтон: он запоминает зону в момент создания,
и после смены часового пояса (поездка) «сегодня» и группировка по дням считаются в старой зоне
до перезапуска процесса.

В тестах `FixedTimeProvider(2026-10-20T09:12, Europe/Moscow)` подменяется через `@TestInstallIn`.

## 2.7 Асинхронность

- Репозитории: `Flow` для чтения, `suspend` для записи. Room сам уводит работу с main-потока.
- Комбинирование измерений с порогами — в ViewModel:

```kotlin
val uiState: StateFlow<HomeUiState> =
    combine(
        readingRepo.observeLast(),
        readingRepo.observeRange(from = time.startOfLocalDay(daysAgo = 6), to = time.now()),
        thresholdsRepo.observe(),
        reminderRepo.observeAll(),
        tick,                                      // пересчёт при ON_RESUME / смене даты или зоны
    ) { last, week, th, reminders, _ ->
        val now = time.now().atZone(time.zone())
        HomeUiState.Content(
            dateLabel     = dateFormatter.homeDate(now.toLocalDate()),
            greeting      = greetingFormatter.format(greetingSlot(now.toLocalTime())),
            last          = last?.let { lastCardMapper.map(it, th, now) },
            week          = weekChartMapper.map(week, th, now),
            nextReminder  = nextReminderFormatter.format(nextReminder(reminders, now), now),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)
```

Подготовка путей графика на 30 дней выполняется на `Dispatchers.Default`, не на main.

## 2.8 Контракт экрана

Каждая фича объявляет три типа:

```kotlin
sealed interface EntryUiState {
    data object Loading : EntryUiState
    data class  Content(
        val sys: String, val dia: String, val pulse: String,
        val activeField: Field,
        val status: StatusPreview,
        val arm: Arm, val tags: List<TagUi>, val comment: String,
        val timeLabel: String,
        val isEdit: Boolean,
        val canSave: Boolean,
        val error: ValidationError? = null,
    ) : EntryUiState
}

sealed interface EntryEvent {
    data class KeyPressed(val key: Key) : EntryEvent
    data class FieldFocused(val field: Field) : EntryEvent
    data class ArmChanged(val arm: Arm) : EntryEvent
    data class TagToggled(val tag: Tag) : EntryEvent
    data class CommentChanged(val text: String) : EntryEvent
    data object SaveClicked : EntryEvent
    data object CloseClicked : EntryEvent
    data object DeleteClicked : EntryEvent
}

sealed interface EntryEffect {                       // одноразовое, через Channel
    data object NavigateHome : EntryEffect
    data object NavigateBack : EntryEffect
    data class  ShowToast(val textRes: Int) : EntryEffect
    data object ShowCriticalHint : EntryEffect       // САД ≥ 180 или ДАД ≥ 120, см. 09.6
}
```

Composable экрана stateless: `@Composable fun EntryScreen(state: EntryUiState, onEvent: (EntryEvent) -> Unit)`.
Это даёт превью всех состояний и скриншот-тесты без Hilt.

## 2.9 Правила, нарушение которых блокирует ревью

1. `Context` не попадает в ViewModel (кроме `@ApplicationContext` внутри data-слоя).
2. В UI-слое нет вычислений статуса, средних, процентов — только отрисовка.
3. Навигация не делается по изменению `UiState` в `LaunchedEffect` — только через `Effect`.
4. Нет `collectAsState()` на `Flow` репозитория прямо в composable.
5. Нет строк в коде: только `stringResource` / `pluralStringResource`. Исключение — SQL предзаполнения
   напоминаний в `onCreate` базы ([03.2](03-data-model.md#предзаполнение)).
6. Нет целей нажатия меньше 48 dp (`Modifier.minimumInteractiveComponentSize()` либо явный размер).
7. Нет `Flow` внутри `UiState` (например, `Flow<PagingData>` отдаётся отдельным свойством ViewModel).
