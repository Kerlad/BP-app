# 04. Дизайн-система

Источник истины — `design_handoff_bp_diary/README.md`. Здесь — как это превратить в тему Compose.

## 4.1 Три направления

Направления `1a Персик` / `1b Шалфей` / `1c Слива` различаются **только токенами**: палитра, шрифт,
радиус карточек. Структура экранов одинаковая. Поэтому направление — это параметр темы, а не ветка кода.

| Направление | Шрифт | Вес цифр | Радиус карточки `r` |
|---|---|---|---|
| PEACH | Nunito | 800 | 28 dp |
| SAGE | Golos Text | 700 | 14 dp |
| PLUM | Onest | 700 | 22 dp |

Производные радиусы: клавиши клавиатуры и поля ввода — `r * 0.6`; чипы — pill (`CircleShape`).

**Заказчик выбрал SAGE (Шалфей)** — оно по умолчанию в DataStore и единственное в релизе.
PEACH и PLUM остаются в коде (стоимость поддержки нулевая — это три `data object` с цветами),
переключателя направления в UI нет.

### Особенности палитры Шалфей

- В Шалфее `ac == ok` и `acs == okS` (light: `#557A47`/`#DAE5CC`, dark: `#9BC285`/`#2C3B25`).
  Линия САД на графике, активное поле ввода, выбранный тег и статус «В норме» — один зелёный.
  Это решение дизайна, а не ошибка, но легенда графика («Верхнее») не должна полагаться только на цвет.
- **Контраст.** NFR требует ≥ 4.5:1 для текста. Предварительная оценка для текста чипов статуса
  13/700 (это не «крупный текст» по WCAG): `ok` на `okS` ≈ 3.8:1, `hi` на `hiS` ≈ 3.6:1 — **ниже нормы**.
  До реализации экранов проверить точным инструментом все пары (`ok/okS`, `hi/hiS`, `lo/loS`, `mut/bg`,
  `mut/sf`, `aci/ac`) в обеих темах и согласовать с дизайнером: затемнить текст чипа или зафиксировать исключение.

## 4.2 Токены

Material 3 `ColorScheme` не покрывает `hi/hiS/ok/okS/lo/loS` и две поверхности `sf`/`sf2`.
Поэтому: **свои токены через `CompositionLocal`**, а `MaterialTheme` настраивается производно
от них (чтобы работали стандартные компоненты — Switch, TimePicker, Snackbar).

```kotlin
@Immutable
data class BpColors(
    val bg: Color,      // фон экрана
    val sf: Color,      // карточки, поверхность
    val sf2: Color,     // навбар, степперы
    val ink: Color,     // основной текст
    val mut: Color,     // вторичный текст
    val ac: Color,      // акцент
    val aci: Color,     // текст на акценте
    val acs: Color,     // мягкий акцент (пилюли, фон активной навигации)
    val hi: Color, val hiS: Color,   // повышенное + мягкий фон
    val ok: Color, val okS: Color,   // норма
    val lo: Color, val loS: Color,   // пониженное
    val ln: Color,      // линии, обводки, сетка графика
)

@Immutable
data class BpShapes(val card: Dp, val key: Dp)   // key = card * 0.6

val LocalBpColors = staticCompositionLocalOf<BpColors> { error("no BpColors") }
val LocalBpShapes = staticCompositionLocalOf<BpShapes> { error("no BpShapes") }

object BpTheme {
    val colors: BpColors @Composable @ReadOnlyComposable get() = LocalBpColors.current
    val shapes: BpShapes @Composable @ReadOnlyComposable get() = LocalBpShapes.current
}
```

Порядок токенов в хендоффе фиксирован — он же в конструкторе `BpColors`, чтобы списки цветов
переносились один-в-один и ревью сводилось к сверке 15 значений:

```
bg, sf, sf2, ink, mut, ac, aci, acs, hi, hiS, ok, okS, lo, loS, ln
```

### Значения

```kotlin
object Peach {
    val light = BpColors(
        bg = Color(0xFFFAF3EC), sf = Color(0xFFFFFFFF), sf2 = Color(0xFFF4E8DE),
        ink = Color(0xFF2D2420), mut = Color(0xFF7B6B62),
        ac = Color(0xFFDD6B4A), aci = Color(0xFFFFFFFF), acs = Color(0xFFFADFD3),
        hi = Color(0xFFC9443A), hiS = Color(0xFFF8DCD7),
        ok = Color(0xFF3F8B5E), okS = Color(0xFFDCEEDF),
        lo = Color(0xFF3F72B5), loS = Color(0xFFDCE7F5),
        ln = Color(0xFFECDDD1),
    )
    val dark = BpColors(
        bg = Color(0xFF1C1613), sf = Color(0xFF271F1B), sf2 = Color(0xFF322823),
        ink = Color(0xFFF6ECE5), mut = Color(0xFFB3A197),
        ac = Color(0xFFF08766), aci = Color(0xFF2A130B), acs = Color(0xFF47291E),
        hi = Color(0xFFF17A6E), hiS = Color(0xFF4A2421),
        ok = Color(0xFF7FC79A), okS = Color(0xFF1F3A2A),
        lo = Color(0xFF86ADE4), loS = Color(0xFF1F2E44),
        ln = Color(0xFF3B302A),
    )
}
```

`Sage` и `Plum` — те же 15 значений из хендоффа в том же порядке.

### Маппинг в MaterialTheme

```kotlin
private fun BpColors.toMaterial(dark: Boolean) = if (dark) darkColorScheme(
    primary = ac, onPrimary = aci, primaryContainer = acs, onPrimaryContainer = ink,
    background = bg, onBackground = ink, surface = sf, onSurface = ink,
    surfaceVariant = sf2, onSurfaceVariant = mut, outline = ln, error = hi,
) else lightColorScheme(/* те же соответствия */)
```

## 4.3 Типографика

```kotlin
@Immutable
data class BpTypography(
    val screenTitle: TextStyle,   // 28 / 700
    val greeting: TextStyle,      // 24 / 700
    val panelTitle: TextStyle,    // 20 / 700
    val cardTitle: TextStyle,     // 16 / 700
    val button: TextStyle,        // 18 / 700
    val body: TextStyle,          // 15 / 400
    val secondary: TextStyle,     // 14 / 400
    val caption: TextStyle,       // 13 / 400
    val micro: TextStyle,         // 12 / 400
    val unit: TextStyle,          // 14 / 500  «мм рт. ст.» на Главной
    val key: TextStyle,           // 24 / 600  цифры клавиатуры
    val reminderTitle: TextStyle, // 15 / 600  название напоминания
    val chip: TextStyle,          // 13 / 700  чип статуса
    val navActive: TextStyle,     // 12 / 700  подпись активного пункта навигации
    val navInactive: TextStyle,   // 12 / 500
    val slash: TextStyle,         // 40 / 400  «/» в «128/82» на Главной, цвет mut
    val numHero: TextStyle,       // 62 / nw, letterSpacing -0.03em
    val numEntry: TextStyle,      // 36 / nw
    val numReminder: TextStyle,   // 32 / nw
    val numStat: TextStyle,       // 26 / nw
    val numRow: TextStyle,        // 22 / nw
)
```

Правила:
- Цифры давления всегда весом `nw` направления (800 для Nunito, 700 для остальных).
- `numHero` имеет `letterSpacing = (-0.03).em` — иначе «128/82» в 62 sp выглядит разряженным.
- **`TextUnit` в `sp`, не `dp`** — приложение обязано масштабироваться системным шрифтом.
  Единственное исключение: цифры на клавишах клавиатуры и `numHero` ограничиваются
  `TextAutoSize`/`nonScaledSp` сверху, чтобы при 200% масштабе «128/82» не ломало карточку
  (см. [4.7](#47-крупный-шрифт)).
- Шрифт **Golos Text кладётся в APK** (`res/font`), начертания 400/500/600/700 (~300–400 КБ).
  Downloadable Fonts не используются: они зависят от Google Play Services и первого запуска с сетью,
  а приоритет заказчика — лёгкий быстрый запуск без внешних зависимостей. Лицензия OFL — в
  `assets/licenses/` и в «О приложении».
- Для PEACH/PLUM `FontFamily` в коде остаётся, но без файлов шрифтов фолбэк — Golos Text.

```kotlin
val golosText = FontFamily(
    Font(R.font.golos_text_regular,  FontWeight.W400),
    Font(R.font.golos_text_medium,   FontWeight.W500),
    Font(R.font.golos_text_semibold, FontWeight.W600),
    Font(R.font.golos_text_bold,     FontWeight.W700),
)
```

## 4.4 Отступы и размеры

```kotlin
object Spacing {
    val xs = 8.dp; val s = 10.dp; val m = 12.dp
    val md = 14.dp; val l = 16.dp; val xl = 20.dp
}
```

| Что | Значение |
|---|---|
| Горизонтальные поля экранов | 20 dp (экран ввода — 16 dp) |
| Зазор между карточками | 14 dp (на экране напоминаний 12 dp) |
| Внутренний padding карточки | 20 dp |
| Высота нижней навигации | 74 dp (фон `sf2`) |
| Главная кнопка «Записать давление» | 62 dp |
| Кнопка «Сохранить» | 56 dp |
| Клавиша клавиатуры | 50 dp, gap 8 dp |
| Круглая иконка-кнопка | 44 dp |
| Переключатель | 52×32 dp, ход ручки 20 dp, анимация 150 ms |
| Цветная полоса строки дневника | 4×38 dp |

**Теней нет.** Поверхности различаются только тоном (`bg` / `sf` / `sf2`).
`Card` из Material 3 использовать с `elevation = 0.dp` или просто `Box` + `background` + `clip`.

## 4.5 Компоненты `core:ui`

| Компонент | Назначение | Параметры |
|---|---|---|
| `BpCard` | карточка `sf` + радиус `r` + padding | `padding`, `onClick?` |
| `StatusChip` | пилюля статуса | `status: Status`, 13/700, padding 5×12 |
| `BigReading` | «128/82 мм рт. ст.» | `sys`, `dia`, `size: Hero\|Entry\|Row` |
| `PulseRow` | иконка `favorite` + значение + подпись | `pulse`, `outOfRange: Boolean` |
| `SegmentedToggle` | pill-трек `sf`, активный `ac`/`aci` | `options`, `selected` |
| `FilterChipRow` | фильтры дневника / теги | `items`, `multiSelect` |
| `Stepper` | «− значение +», шаг 5 | `value`, `range`, `onChange` |
| `BpSwitch` | переключатель по спеке хендоффа | `checked` |
| `NumPad` | клавиатура 3×4 | `onKey: (Key) -> Unit`, `actionIcon` |
| `BpToast` | тост `ink`/`bg` над навигацией, 2.4 с | `text`, `icon` |
| `DashedThresholdLine` | пунктир порога на графике | `y`, `color` |

Все компоненты имеют `@Preview` в светлой и тёмной теме для всех трёх направлений
(`@PreviewParameter` с провайдером направлений) — это основа скриншот-тестов ([10](10-testing.md)).

## 4.6 Иконки

Material Symbols Rounded. Набор фиксирован, иконки подключаются как локальные векторы
(`material-symbols` → экспорт в `res/drawable`), а не целиком библиотекой:

`tune, add, favorite, alarm, chevron_right, close, schedule, backspace, arrow_forward, check,
monitoring, menu_book, home, notifications, add_alarm, remove, arrow_back, dark_mode, check_circle`

`favorite` используется в filled-варианте цветом `hi`.

## 4.7 Крупный шрифт

Аудитория 30–60 лет, многие с системным масштабом 130–200%.

1. Все вертикальные размеры карточек — `wrapContentHeight`, а не фиксированные `height`.
2. Карточка последнего измерения при `fontScale > 1.3`: `numHero` уменьшается до 48 sp,
   «мм рт. ст.» переносится на следующую строку. Запрет переноса внутри `128/82` остаётся.
3. Клавиатура: цифры 24 sp не масштабируются выше 1.2× (клавиша фиксирована 50 dp,
   иначе сетка 3×4 не влезает в 360 dp).
4. Чипы самочувствия в горизонтальном скролле не переносятся — при большом шрифте просто длиннее лента.
5. Своя настройка «Крупный шрифт» (`font_scale_boost` 1.0 / 1.15 / 1.3, UI — v1.1) **умножается** на
   системную — пользователь, которому неудобно лезть в настройки Android, получает результат в приложении.

## 4.8 Доступность

- `StatusChip`: `contentDescription` = «Статус: повышенное».
- `BigReading`: один объединённый узел семантики `«128 на 82 миллиметра ртутного столба»`,
  вложенные тексты `clearAndSetSemantics`.
- Клавиши: `contentDescription` = «цифра 7», «стереть», «далее», «сохранить».
- Цвет **не единственный носитель смысла**: статус всегда дублируется текстом
  («Повышенное»), в дневнике — текстом в строке-примечании.
- Графики: `contentDescription` с краткой сводкой («График за 7 дней, среднее 133 на 85,
  максимум 152 на 95 шестнадцатого октября»).
- Полоса «В пределах ваших уровней» — семантика `ProgressBarRangeInfo` + текст с процентом.
