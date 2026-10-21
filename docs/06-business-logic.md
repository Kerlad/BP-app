# 06. Бизнес-логика

Всё в этом документе живёт в `:domain` и покрывается юнит-тестами на JVM. Ни строки этой логики
не должно быть в composable-функциях.

## 6.1 Классификация

Ровно как в прототипе (`cls()`), приоритет HIGH над LOW:

```kotlin
fun classify(sys: Int, dia: Int, th: Thresholds): Status = when {
    sys >= th.highSys || dia >= th.highDia -> Status.HIGH
    sys <= th.lowSys  || dia <= th.lowDia  -> Status.LOW
    else                                    -> Status.NORMAL
}

// для средних значений дня (столбцы «7 дней») — без округления, как cls(as, ad) в прототипе
fun classify(sys: Double, dia: Double, th: Thresholds): Status = when {
    sys >= th.highSys || dia >= th.highDia -> Status.HIGH
    sys <= th.lowSys  || dia <= th.lowDia  -> Status.LOW
    else                                    -> Status.NORMAL
}

fun pulseOutOfRange(pulse: Int?, th: Thresholds): Boolean =
    pulse != null && (pulse < th.pulseLow || pulse > th.pulseHigh)
```

Важные следствия, которые нужно зафиксировать тестами:

| Вход (пороги 135/85, 100/60) | Статус | Почему |
|---|---|---|
| 120/90 | HIGH | достаточно одного превышения |
| 136/70 | HIGH | САД на пороге — уже «повышенное» (`>=`) |
| 135/85 | HIGH | граница включительно |
| 134/84 | NORMAL | |
| 100/70 | LOW | САД на нижней границе (`<=`) |
| 145/55 | HIGH | HIGH проверяется первым, хотя ДАД низкое |
| 101/61 | NORMAL | |
| 134.5/80.0 (средние дня) | NORMAL | без округления до 135 |
| 135.0/80.0 (средние дня) | HIGH | |

Пульс классифицируется **отдельно** и не влияет на статус измерения: он только подсвечивается
цветом `hi` в строке дневника и в карточке.

Подписи: HIGH → «Повышенное», NORMAL → «В норме», LOW → «Пониженное».
Цвета: `hi/hiS`, `ok/okS`, `lo/loS`.

## 6.2 Ввод с клавиатуры

Алгоритм из прототипа (`press()`), порядок полей `sys → dia → pulse`:

```kotlin
enum class Field { SYS, DIA, PULSE }
sealed interface Key { data class Digit(val d: Char) : Key; data object Del : Key; data object Next : Key }

fun reduce(state: Draft, key: Key): DraftResult = when (key) {

    is Key.Del -> {
        val cur = state.value(state.field)
        if (cur.isEmpty() && state.field != Field.SYS) {
            val prev = state.field.previous()
            // уходим в предыдущее поле И стираем его последнюю цифру
            DraftResult.Updated(state.set(prev, state.value(prev).dropLast(1)).focus(prev))
        } else {
            DraftResult.Updated(state.set(state.field, cur.dropLast(1)))
        }
    }

    is Key.Next ->
        if (state.field != Field.PULSE) DraftResult.Updated(state.focus(state.field.next()))
        else DraftResult.Save

    is Key.Digit -> {
        val cur = state.value(state.field)
        if (cur.length >= 3) DraftResult.Ignored
        else {
            val new = cur + key.d
            val advance = (new.length == 3 || (new.length == 2 && new[0] != '1')) &&
                          state.field != Field.PULSE
            DraftResult.Updated(
                state.set(state.field, new).let { if (advance) it.focus(state.field.next()) else it }
            )
        }
    }
}
```

Почему правило «2 цифры, если первая не 1»: САД/ДАД трёхзначные только начинаясь на «1»
(100–199). «95» — это уже готовое значение, ждать третью цифру незачем. «12…» может быть
«120» или «128» — ждём третью. Это даёт ввод «128 82 71» в 7 нажатий (`1 2 8 8 2 7 1`)
без единого перехода вручную.

Исключение для пульса: автоперехода нет (поле последнее), после третьей цифры фокус остаётся,
действие — `check` → сохранить.

### Таблица тестов ввода

| Нажатия | sys | dia | pulse | Фокус |
|---|---|---|---|---|
| `1 2 8` | 128 | | | DIA |
| `9 5` | 95 | | | DIA |
| `1 2` | 12 | | | SYS (ждём третью) |
| `1 2 8` `8 2` | 128 | 82 | | PULSE |
| `1 2 8` `8 2` `7 1` | 128 | 82 | 71 | PULSE |
| `1 2 8` `del` | 12 | | | SYS |
| `1 2 8` `8 2` `del` `del` `del` | 12 | | | SYS (с пустого DIA ушли в SYS и стёрли «8») |
| `1 2 8 9` | 128 | 9 | | DIA (ждём вторую цифру ДАД) |
| `del` на пустом SYS | | | | SYS (ничего не происходит) |
| `1 2 8` `8 2` `7 1` `5` | 128 | 82 | 715 | PULSE (у пульса нет автоперехода; см. ниже) |
| `1 2 8` `8 2` `1 0 4` `7` | 128 | 82 | 104 | PULSE (4-я цифра игнорируется — `Ignored`) |
| `1 2 8` `8 2` `7 1` `check` | 128 | 82 | 71 | → `DraftResult.Save` |

Строка `1 2 8 9` — тонкость: после `1 2 8` фокус уже в DIA, значит «9» попадает в DIA
и становится его первой цифрой. Четвёртая цифра **не** игнорируется, она приходит уже в следующее поле.
Игнорируется только цифра в поле, где уже 3 цифры (на практике — только PULSE, т.к. из SYS и DIA
после третьей цифры фокус уходит).

Пульс «71» + «5» даёт «715»: у пульса нет автоперехода, три цифры допустимы, значение отсекает
валидация при сохранении (`pulse in 25..250`).

## 6.3 Группировка по дням

```kotlin
// :domain — без строк
fun localDay(r: Reading): LocalDate = r.takenAt.atOffset(r.zoneOffset).toLocalDate()

sealed interface DayLabel {
    data object Today : DayLabel
    data object Yesterday : DayLabel
    data class Date(val day: LocalDate) : DayLabel
}

fun dayLabel(day: LocalDate, today: LocalDate): DayLabel = when (day) {
    today              -> DayLabel.Today
    today.minusDays(1) -> DayLabel.Yesterday
    else               -> DayLabel.Date(day)
}
```

Presentation превращает `DayLabel` в текст из ресурсов: «Сегодня», «Вчера»,
«18 октября, воскресенье». Месяц в родительном падеже («18 октября»), день недели в именительном
(«воскресенье»), с маленькой буквы — как в прототипе. Эти формы — свои `string-array` в ресурсах,
**не** `DateTimeFormatter` с локалью: системный форматтер даёт «окт.» и «Вс».

`today` вычисляется в **текущей** зоне устройства (`TimeProvider.zone()`), а день измерения —
в зоне на момент измерения (`zoneOffset`).

Заголовок даты на Главной — «Вторник, 20 октября» (день недели с заглавной, именительный).
Это **другой** формат, чем в дневнике; оба лежат в `BpDateFormatter` с отдельными методами.

## 6.4 Статистика периода

```kotlin
data class PeriodStats(
    val avgSys: Int, val avgDia: Int, val avgPulse: Int?,   // null, если ни у одной записи нет пульса
    val morning: Avg?, val evening: Avg?,          // < 12:00 / >= 12:00 локального времени
    val max: Reading, val min: Reading,            // по САД
    val countHigh: Int, val countNormal: Int, val countLow: Int,
    val inRangePercent: Int,
)
```

Правила:
- Средние — арифметические по **всем измерениям** периода, округление `Math.round` (как в прототипе;
  семантика «половина вверх» совпадает с JS `Math.round`; `kotlin.math.round` не использовать — он
  округляет половину к чётному). Средний пульс — только по записям с пульсом.
- «Утром»/«Вечером» — по часу локального времени измерения, граница 12:00.
- Максимум/минимум — по САД; при равенстве берётся **позднейшее** измерение
  (прототип берёт первое встреченное при строгом `>` — то есть раннее; фиксируем «позднейшее»
  как более полезное: врачу интереснее недавний скачок. Отступление от прототипа — осознанное).
- `inRangePercent = round(countNormal / total * 100)`.
- Период отсчитывается от начала локального дня `today - (period - 1)` до «сейчас».

### Эталонные значения

Тестовый датасет — генератор `makeData()` из прототипа, seed 11, «сейчас» 20.10.2026 09:12,
зона `Europe/Moscow`. Воспроизводится в `DemoData.kt` **буквально**, включая порядок вызовов `rnd()`:

1. ГПСЧ: `s = 11; rnd() = (s = s * 16807 % 2147483647) / 2147483647.0` (в `Long`).
2. Цикл `d` от 29 до 0, `h` в `[7, 21]`.
3. Для каждой пары: минуты `5 + floor(rnd() * 40)` → время; **если время позже «сейчас» — пропуск**
   (но `rnd()` уже израсходован); далее `sp = rnd() < .12 ? 14 : 0`, `dp = rnd() < .06 ? -24 : 0`,
   затем `rnd()` для sys, dia, pulse — именно в этом порядке.

Итого **59 записей**, а не 60: вечернее измерение 20.10 (21:xx) позже 09:12 и пропускается.

Эталонные значения статистики фиксируются golden-тестом (`stats_golden.json`): любое изменение формул
падает в CI. Эталон получается **независимым** прогоном JS-логики прототипа (`makeData()` + расчёт из
`renderVals()`) в Node, а не из самого Kotlin-кода. Единственное ожидаемое расхождение с прототипом —
правило выбора максимума/минимума при равенстве (см. выше); оно отмечается в тесте комментарием.

## 6.5 Геометрия графиков

Чтобы перенос с SVG на `Canvas` не «поплыл», сохраняем те же формулы.

### Линейный график (экран «Графики»), viewBox 320×200

```kotlin
// Y: ось 50..180 мм рт. ст., 180 px рабочей высоты, базовая линия y = 190
fun yLine(v: Int): Float = 190f - (v.coerceIn(50, 180) - 50) / 130f * 180f

// X: n точек раскладываются равномерно от 34 до 314; одна точка — по центру
fun xLine(i: Int, n: Int): Float = if (n > 1) 34f + i * 280f / (n - 1) else 174f
```

### Столбчатый график «7 дней» (Главная), 300×120

```kotlin
// Y: та же ось, но 104 px высоты, базовая 112
fun yBar(v: Int): Float = 112f - (v.coerceIn(50, 180) - 50) / 130f * 104f

// X: центр колонки дня, i = 0 для сегодня ... 6 для шести дней назад
fun xBar(daysAgo: Int): Float = ((6 - daysAgo) + 0.5f) * 300f / 7f
```

Столбец дня: от `yBar(max SYS за день)` до `yBar(min DIA за день)`; цвет — `classify(avgSys, avgDia)`
по **неокруглённым** средним дня ([6.1](#61-классификация)). Дни без измерений — без столбца.

В Compose эти координаты масштабируются на фактическую ширину единым коэффициентом:
`scale = size.width / 320f` (или `/300f`), а высота канваса задаётся пропорционально —
`width * 200 / 320` (или `width * 120 / 300`). Так геометрия остаётся идентичной прототипу
при любой плотности экрана. Толщины линий и диаметры точек — в dp, не масштабируются.

### Точки

Точка САД рисуется цветом `hi` (диаметр +1), если **статус измерения** HIGH — в том числе из-за ДАД
(в прототипе фильтр `cls(p.sys, p.dia) === 'hi'`).

### Пунктиры

`PathEffect.dashPathEffect(floatArrayOf(4f * scale, 5f * scale))`, цвет `hi`, alpha .7 — линия порога
высокого САД на обоих графиках; на линейном дополнительно порог высокого ДАД.

## 6.6 Следующее напоминание

```kotlin
fun nextReminder(reminders: List<Reminder>, now: ZonedDateTime): Reminder? {
    val enabled = reminders.filter { it.enabled }
    if (enabled.isEmpty()) return null
    return enabled
        .map { it to it.nextTriggerAfter(now) }
        .minByOrNull { it.second }!!
        .first
}
```

Прототип упрощает: берёт первое время больше текущего, иначе первое по списку, **без учёта дней недели**.
В продакшне учитываем `days`.

Формат на Главной (Presentation):

| Срабатывание | Текст |
|---|---|
| сегодня | `"14:00 · Дневное измерение"` |
| завтра | `"Завтра, 08:00 · Утреннее измерение"` |
| позже | `"Пн, 08:00 · Утреннее измерение"` (сокращённый день недели) |
| нет включённых | `null` → «Напоминания выключены» |

Пример: если включено только напоминание «Пн – Пт» 08:00, то в субботу вечером Главная показывает
`"Пн, 08:00 · …"`, а не `"08:00 · …"`.

Домен возвращает `Reminder` + `ZonedDateTime` срабатывания; «сегодня/завтра/день недели» и текст
определяет Presentation.

## 6.7 Пересчёт при смене порогов

Поскольку статус не хранится, никакого «пересчёта» в данных нет: меняется `Thresholds`
в DataStore → `Flow` отдаёт новое значение → все `combine` в ViewModel пересобирают `UiState`
→ Compose перерисовывает цвета. Единственное требование: `Thresholds` — `data class`
(корректный `equals`) и поток с `distinctUntilChanged`, иначе лишние рекомпозиции.

Paging-дневник получает пороги как параметры `PagingSource` ([03.3](03-data-model.md#33-dao)),
поэтому при смене порогов нужно **инвалидировать** источник:

```kotlin
val diaryFlow = combine(filter, thresholds) { f, th -> f to th }
    .flatMapLatest { (f, th) -> pager(f, th).flow }   // новый Pager -> новый PagingSource
    .cachedIn(viewModelScope)
```

## 6.8 Форматирование чисел

- Давление: `"$sys/$dia"`, без пробелов вокруг слэша; в вёрстке слэш — отдельный текст
  40 sp/400 цветом `mut`, цифры — `nw`.
- Единицы: «мм рт. ст.» (с пробелами, неразрывными: `мм рт. ст.`).
- Пульс: «уд/мин». Пульс `null` не отображается (ни значение, ни иконка).
- Проценты: целое, без десятых.
- Склонение «измерение/измерения/измерений» — `plurals`, ключ `measurements_count`.
