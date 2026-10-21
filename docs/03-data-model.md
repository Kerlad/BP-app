# 03. Модель данных

## 3.1 Доменные модели (`core/model`, без Android-зависимостей)

```kotlin
enum class Arm { LEFT, RIGHT }

enum class Status { HIGH, NORMAL, LOW }           // вычисляется, не хранится

// Стабильные ключи хранятся в БД, CSV-импорте и бэкапе; русские подписи — только в ресурсах и CSV-экспорте.
// Порядок enum = порядок чипов на экране ввода.
enum class Tag { GOOD, HEADACHE, DIZZINESS, WEAKNESS, AFTER_MEDICINE, AFTER_EXERCISE }
// Хорошо, Головная боль, Головокружение, Слабость, После лекарства, После нагрузки

data class Reading(
    val id: Long = 0,
    val takenAt: Instant,                         // момент измерения
    val zoneOffset: ZoneOffset,                   // смещение в момент измерения (нужно для localDay)
    val sys: Int,                                 // мм рт. ст.
    val dia: Int,
    val pulse: Int?,                              // уд/мин; null только у импортированных записей без пульса
    val arm: Arm,
    val tags: Set<Tag> = emptySet(),
    val comment: String = "",
    val profileId: Long = DEFAULT_PROFILE,        // задел на v2
)

data class Thresholds(
    val highSys: Int = 135,
    val highDia: Int = 85,
    val lowSys: Int = 100,
    val lowDia: Int = 60,
    val pulseLow: Int = 50,
    val pulseHigh: Int = 90,
)

data class Reminder(
    val id: Long = 0,
    val time: LocalTime,
    val label: String,
    val days: Set<DayOfWeek>,                     // пустое множество недопустимо
    val enabled: Boolean,
)
```

`Thresholds` по умолчанию совпадают с состоянием прототипа: `{hs:135, hd:85, ls:100, ld:60, pl:50, ph:90}`.

**Пульс `null`.** При ручном вводе пульс обязателен (FR-1.4). `null` появляется только при импорте
CSV из программ, которые пульс не пишут ([13.2](13-csv.md#валидация-строк)). Такая запись не участвует
в среднем пульсе, в UI пульс не показывается, `pulseOutOfRange(null) = false`.

## 3.2 Room: схема

### Таблица `readings`

```kotlin
@Entity(
    tableName = "readings",
    indices = [
        Index(value = ["taken_at"]),                              // сортировка/диапазоны
        Index(value = ["profile_id", "taken_at"]),
    ],
)
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "taken_at")   val takenAt: Long,           // epoch millis, UTC
    @ColumnInfo(name = "tz_offset")  val tzOffsetSeconds: Int,    // смещение в момент записи
    @ColumnInfo(name = "sys")        val sys: Int,
    @ColumnInfo(name = "dia")        val dia: Int,
    @ColumnInfo(name = "pulse")      val pulse: Int?,             // NULL — пульс не указан (импорт)
    @ColumnInfo(name = "arm")        val arm: String,             // "LEFT" | "RIGHT"
    @ColumnInfo(name = "tags")       val tags: String,            // JSON-массив ключей Tag: ["HEADACHE"]
    @ColumnInfo(name = "comment")    val comment: String,
    @ColumnInfo(name = "profile_id") val profileId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,  // soft delete для «Отменить»
)
```

Почему так:

- **`taken_at` в UTC + `tz_offset`.** Пользователь мерит давление в поездках. Для группировки
  по «дню, когда мерил» нужен локальный день **на момент измерения**, а не текущая зона.
  `tz_offset` позволяет восстановить тот локальный день, не теряя абсолютного времени.
- **`tags` как JSON-строка ключей.** Теги из фиксированного списка 6 штук, выборки по тегу в MVP нет.
  Хранятся ключи enum (`HEADACHE`), а не русские подписи — локализация (v2) не потребует миграции данных.
  Отдельная таблица `reading_tags` понадобится, когда появится фильтр/статистика по тегам — это v1.2,
  см. [3.6](#36-миграции).
- **`deleted_at`.** Удаление в дневнике даёт snackbar «Отменить» на 5 с. Soft delete проще,
  чем держать удалённую запись в памяти, и переживает смерть процесса. Физическая чистка
  записей, удалённых **более 24 ч назад**, — раз в сутки через `WorkManager` и при запуске приложения.
  Дольше хранить удалённые данные о здоровье незачем. «Удалить все данные» и импорт «Заменить всё»
  удаляют физически, сразу.
- **Статуса в таблице нет** — см. [принцип 3](02-architecture.md#21-принципы).

### Таблица `reminders`

```kotlin
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "minutes_of_day") val minutesOfDay: Int,   // 0..1439
    @ColumnInfo(name = "label")          val label: String,
    @ColumnInfo(name = "days_mask")      val daysMask: Int,       // битовая маска, Пн = 1 shl 0
    @ColumnInfo(name = "enabled")        val enabled: Boolean,
)
```

`minutesOfDay` вместо строки `"08:00"`: сортировка и поиск следующего срабатывания — арифметика,
а не сравнение строк. `daysMask` вместо текста `"Пн – Пт"`: текст собирается в Presentation
(`DaysFormatter`: полная маска → «Ежедневно», 0b0011111 → «Пн – Пт», иначе «Пн, Ср, Пт»).

### Предзаполнение

Три напоминания из хендоффа создаются в `onCreate` коллбэка базы (а не в коде ViewModel):

```kotlin
Room.databaseBuilder(...)
    .addCallback(object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT INTO reminders (minutes_of_day,label,days_mask,enabled) VALUES " +
                "(480,'Утреннее измерение',127,1)," +
                "(840,'Дневное измерение',31,0)," +
                "(1260,'Вечернее измерение',127,1)")
        }
    })
```

После `onCreate` обязательно вызвать перепланирование алармов (см. [07](07-reminders.md)).
SQL с русскими названиями здесь — единственное допустимое исключение из правила «нет строк в коде».

## 3.3 DAO

```kotlin
@Dao
interface ReadingDao {

    @Query("SELECT * FROM readings WHERE deleted_at IS NULL ORDER BY taken_at DESC LIMIT 1")
    fun observeLast(): Flow<ReadingEntity?>

    @Query("""SELECT * FROM readings
              WHERE deleted_at IS NULL AND taken_at BETWEEN :from AND :to
              ORDER BY taken_at ASC""")
    fun observeRange(from: Long, to: Long): Flow<List<ReadingEntity>>

    // дневник: Paging 3, фильтр по статусу делается в SQL через пороги — см. 3.4
    @Query("""SELECT * FROM readings
              WHERE deleted_at IS NULL
                AND (:all = 1
                     OR (:status = 'HIGH'   AND (sys >= :hs OR dia >= :hd))
                     OR (:status = 'LOW'    AND NOT (sys >= :hs OR dia >= :hd) AND (sys <= :ls OR dia <= :ld))
                     OR (:status = 'NORMAL' AND NOT (sys >= :hs OR dia >= :hd) AND NOT (sys <= :ls OR dia <= :ld)))
              ORDER BY taken_at DESC""")
    fun pagingSource(
        all: Int, status: String,
        hs: Int, hd: Int, ls: Int, ld: Int,
    ): PagingSource<Int, ReadingEntity>

    @Insert suspend fun insert(e: ReadingEntity): Long
    @Update suspend fun update(e: ReadingEntity)

    @Query("UPDATE readings SET deleted_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE readings SET deleted_at = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("DELETE FROM readings WHERE deleted_at IS NOT NULL AND deleted_at < :before")
    suspend fun purge(before: Long)

    @Query("DELETE FROM readings")
    suspend fun hardDeleteAll()                    // «Удалить все данные», импорт «Заменить всё»

    @Query("SELECT * FROM readings WHERE id = :id AND deleted_at IS NULL")
    suspend fun getById(id: Long): ReadingEntity?  // экран редактирования

    @Query("SELECT COUNT(*) FROM readings WHERE deleted_at IS NULL")
    fun observeCount(): Flow<Int>
}
```

Для `PagingSource` из Room нужен артефакт `androidx.room:room-paging`.

Фильтр по статусу **в SQL, а не в памяти**: иначе Paging отдаёт полупустые страницы,
а на истории в несколько тысяч записей фильтрация в Kotlin грузит main-поток при скролле.
Порядок условий в запросе повторяет приоритет классификации из [06](06-business-logic.md#61-классификация):
HIGH проверяется первым.

## 3.4 Агрегаты для графиков

Для 7 и 30 дней выборка мелкая (до ~60 записей) — считаем в Kotlin, в `Dispatchers.Default`.
Это же относится к столбчатому графику «7 дней» на Главной: он строится в Kotlin из `observeRange`
([06.5](06-business-logic.md#65-геометрия-графиков)), SQL-агрегат для него **не** используется —
«среднее 133/85» в заголовке карточки считается по всем измерениям, а не по средним дней.

Для 90 дней и «всё время» (v1.1) — агрегирующий запрос:

```kotlin
@Query("""SELECT strftime('%Y-%m-%d', taken_at/1000, 'unixepoch', tz_offset || ' seconds') AS day,
                 AVG(sys) AS avgSys, AVG(dia) AS avgDia, AVG(pulse) AS avgPulse,
                 MAX(sys) AS maxSys, MIN(dia) AS minDia, COUNT(*) AS n
          FROM readings
          WHERE deleted_at IS NULL AND taken_at BETWEEN :from AND :to
          GROUP BY day ORDER BY day ASC""")
fun observeDailyAggregates(from: Long, to: Long): Flow<List<DailyAggregate>>
```

`AVG(pulse)` в SQLite игнорирует `NULL` — записи без пульса в средний пульс не попадают.

## 3.5 DataStore (Preferences)

Файл `settings.preferences_pb`:

| Ключ | Тип | По умолчанию |
|---|---|---|
| `th_high_sys` | Int | 135 |
| `th_high_dia` | Int | 85 |
| `th_low_sys` | Int | 100 |
| `th_low_dia` | Int | 60 |
| `th_pulse_low` | Int | 50 |
| `th_pulse_high` | Int | 90 |
| `last_arm` | String | `LEFT` |
| `theme_mode` | String | `SYSTEM` |
| `direction` | String | `SAGE` (решение заказчика; `PEACH`/`PLUM` остаются в коде) |
| `font_scale_boost` | Float | 1.0 |
| `onboarding_done` | Bool | false |
| `disclaimer_ack` | Int | 0 — версия принятого дисклеймера; экран показывается, если меньше `DISCLAIMER_VERSION` ([09.6](09-privacy-security.md#96-медицинские-дисклеймеры)) |

Ключей для имени пользователя и согласия на крэш-репорты **нет** (решения заказчика: приветствие
без имени, сторонних SDK нет).

`ThresholdsRepository.observe()` возвращает `Flow<Thresholds>` c `.distinctUntilChanged()` —
иначе каждое чтение DataStore перерисовывает графики.

## 3.6 Миграции

Правило: `fallbackToDestructiveMigration()` **запрещён** — это дневник здоровья за годы.
Каждая миграция сопровождается тестом (`MigrationTestHelper`) и экспортируется схема
(`room.schemaLocation` → `app/schemas`, схемы в git).

Приложение ещё не выпущено, поэтому схема стартует с версии 1 **сразу со всеми колонками**
из 3.2 (`deleted_at`, `updated_at`, `created_at`, `tz_offset`, nullable `pulse`). Миграции «для
себя» до первого релиза не пишутся: пока нет установок у пользователей, схема v1 правится напрямую.

| Версия | Релиз | Изменение | Миграция |
|---|---|---|---|
| 1 | v1.0 | `readings`, `reminders` (полная схема 3.2) | — |
| 2 | v1.1 | только если потребуется для истории изменений записи (решается при планировании v1.1) | `MIGRATION_1_2` + тест |
| следующая | v1.2 | таблицы `tags`, `reading_tags`; перенос JSON-ключей в связи | Room-миграция + разбор JSON вручную |
| следующая | v2 | таблица `profiles`, FK `readings.profile_id` | создать профиль «Я», привязать существующие записи |

## 3.7 Валидация на входе в data-слой

Use case `SaveReadingUseCase` (`:domain`) отклоняет запись до обращения к репозиторию:

```kotlin
enum class Reason { SYS_RANGE, DIA_RANGE, PULSE_RANGE, PULSE_MISSING, SYS_NOT_GREATER, FUTURE_TIME }

data class ValidationError(val field: Field?, val reason: Reason)   // field = null для FUTURE_TIME

sealed interface SaveResult {
    data class Success(val id: Long) : SaveResult
    data class Invalid(val error: ValidationError) : SaveResult
}
```

Правила (из FR-1.5): `sys in 50..300`, `dia in 30..200`, `pulse in 25..250`, `sys > dia`,
`takenAt <= time.now()`. Для ручного ввода `pulse` обязателен (`PULSE_MISSING`); для импорта
допустим `null`. Пороговые значения вынесены в `ValidationRules` — на них есть юнит-тесты.

Тексты ошибок (под сеткой полей ввода): `SYS_NOT_GREATER` — «Верхнее должно быть больше нижнего».
Остальные тексты **не утверждены** — согласовать с заказчиком до реализации экрана ввода.
