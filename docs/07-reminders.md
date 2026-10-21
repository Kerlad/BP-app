# 07. Напоминания

Самая хрупкая часть приложения: Android активно убивает отложенную работу. Напоминание, которое
не пришло, обесценивает весь продукт — пользователь перестаёт мерить давление.

## 7.1 Выбор механизма

| Вариант | Годится? |
|---|---|
| `WorkManager` периодический | **Нет.** Минимальный интервал 15 мин и окно исполнения — напоминание в 08:00 может прийти в 08:40 |
| `AlarmManager.setRepeating` | Нет. С API 19 неточный и шатает время |
| `AlarmManager.setExactAndAllowWhileIdle` + перепланирование | **Да.** Один аларм на ближайшее срабатывание, после выстрела планируется следующее |
| `setAlarmClock` | Да, как вариант: не ограничивается Doze и показывает иконку будильника. Подходит, но иконка в статус-баре смущает пользователей; на API 31+ тоже требует разрешения на точные будильники |

**Решение:** `setExactAndAllowWhileIdle` на ближайшее срабатывание **одного** напоминания-кандидата
(минимум по времени среди всех включённых), после срабатывания — перепланирование.
Плюс `WorkManager` раз в сутки как «сторож»: проверяет, что аларм стоит, и восстанавливает.

## 7.2 Разрешения

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<!-- USE_EXACT_ALARM НЕ объявляется, см. ниже -->
```

### `POST_NOTIFICATIONS` (Android 13+)
Запрашивается в онбординге на шаге «Напоминания» — в контексте, а не при первом запуске.
Отказ не блокирует приложение: на экране напоминаний остаётся баннер с кнопкой.

### `SCHEDULE_EXACT_ALARM`
- Android 12–13 (API 31–33): выдаётся при установке, но пользователь может отозвать.
- Android 14+ (API 34+): для новых установок приложений с targetSdk ≥ 33 **по умолчанию не выдано** —
  нужно отправить пользователя в настройки через `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.
- Запрашивается в онбординге на шаге «Напоминания» (с пояснением) и с баннера на экране напоминаний.

### Почему без `USE_EXACT_ALARM`
`USE_EXACT_ALARM` выдаётся без диалога, но политика Google Play разрешает его только приложениям,
чья основная функция — будильник или календарь. Дневник давления с напоминаниями под это не подпадает,
и объявление разрешения грозит отклонением при ревью. Поэтому в манифесте его **нет**.

**Стратегия:** проверяем `alarmManager.canScheduleExactAlarms()`; при `false` показываем баннер
с переходом в настройки и **деградируем** на `setWindow(..., windowLengthMillis = 10 мин)`.
Напоминание придёт с небольшой задержкой, но придёт.

Изменение разрешения пользователем ловим ресивером на
`AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` → `rescheduleAll()`.

```kotlin
private fun schedule(at: Long, pi: PendingIntent) {
    when {
        Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms() ->
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else ->
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pi)
    }
}
```

Код работает и без точных алармов — это штатный режим, а не авария.

## 7.3 Планирование

```kotlin
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val am: AlarmManager,
    private val repo: ReminderRepository,
    private val time: TimeProvider,
) {
    suspend fun rescheduleAll() {
        cancelReminderAlarm()                       // НЕ трогает отложенное (REQ_SNOOZE)
        val now = time.now().atZone(time.zone())
        val next = repo.enabled().mapNotNull { r -> r.nextTriggerAfter(now)?.let { r to it } }
            .minByOrNull { it.second } ?: return
        schedule(next.second.toInstant().toEpochMilli(), reminderPendingIntent(next.first.id))
    }

    fun snooze(reminderId: Long) {                 // «Отложить на 15 минут»
        schedule(time.now().toEpochMilli() + 15 * 60_000L, snoozePendingIntent(reminderId))
    }
}
```

Два независимых `PendingIntent` с разными `requestCode`: `REQ_REMINDER = 1000` (ближайшее
напоминание) и `REQ_SNOOZE = 9000` (отложенное). `rescheduleAll()` отменяет только первый —
иначе любое перепланирование (переключили напоминание, сработал сторож) молча снимет отложенное.
«Удалить все данные» отменяет оба.

`Reminder.nextTriggerAfter(now)`: ближайший `DayOfWeek` из набора, у которого
`date.atTime(time) > now`. Обрабатывать переход через неделю (сегодня пятница 22:00,
напоминание «Пн 08:00» → следующий понедельник).

### Когда вызывается `rescheduleAll()`

| Событие | Приёмник |
|---|---|
| Срабатывание аларма | `ReminderReceiver` (после показа уведомления) |
| Создание/изменение/удаление напоминания | ViewModel экрана редактирования |
| Переключатель вкл/выкл | ViewModel списка |
| `BOOT_COMPLETED` | `BootReceiver` |
| `MY_PACKAGE_REPLACED` | `BootReceiver` (обновление приложения сбрасывает алармы) |
| `TIMEZONE_CHANGED`, `TIME_SET` | `BootReceiver` |
| `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` | `BootReceiver` |
| Ежесуточный «сторож» | `WorkManager` (`ReminderWatchdogWorker`) |
| Первый запуск после `onCreate` базы | `Application.onCreate` → одноразовый `WorkManager` |

```xml
<receiver android:name=".reminders.BootReceiver" android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
        <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
        <action android:name="android.intent.action.TIMEZONE_CHANGED" />
        <action android:name="android.intent.action.TIME_SET" />
        <action android:name="android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" />
    </intent-filter>
</receiver>
```

`BootReceiver` обязан отдать управление быстро: внутри — `goAsync()` + корутина,
либо постановка `OneTimeWorkRequest`. Второй вариант надёжнее.

## 7.4 Уведомление

- Канал: `reminders`, `IMPORTANCE_HIGH`, имя «Напоминания об измерении», вибрация, без звука-будильника
  (это не будильник — звук по умолчанию уведомления).
- Заголовок: название напоминания («Утреннее измерение»).
- Текст: «Пора измерить давление».
- Действие 1 (основное нажатие): явный `PendingIntent` на `MainActivity` с extra `open=entry_new` →
  экран ввода. Публичный deep link не используется ([02.4](02-architecture.md#24-навигация)).
- Действие 2: «Отложить на 15 минут» → `ReminderScheduler.snooze()` (свой `requestCode`), уведомление снимается.
- `setAutoCancel(true)`, иконка — монохромная `favorite`/`monitoring`, цвет акцента темы.

Текст уведомления **не содержит медицинских данных** (ни последних значений, ни статуса):
уведомления видны на залоченном экране, а это данные о здоровье. См. [09](09-privacy-security.md).

## 7.5 Производители с агрессивным энергосбережением

Xiaomi/Huawei/Oppo/Vivo/Samsung убивают алармы фоновых приложений. Поэтому:

Всё в этом разделе — **v1.1**.

1. На экране напоминаний — ссылка «Напоминания не приходят?» → экран с инструкцией:
   отключить оптимизацию батареи для приложения, разрешить автозапуск.
2. Кнопка «Отключить оптимизацию батареи»: `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
   Показывать **только** по явному переходу пользователя из этого экрана —
   Play запрещает навязывать это разрешение (политика по `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
   допускает его для будильников/напоминаний, но обоснование должно быть в листинге).
3. Диагностика: `ReminderWatchdogWorker` пишет в локальный лог факты срабатывания; экран
   «Диагностика напоминаний» (скрытый, через 5 тапов по версии) показывает последние 20 событий.
   Это сильно экономит время на разборе жалоб «не приходит».

## 7.6 Тестирование

| Проверка | Как |
|---|---|
| Планирование следующего срабатывания | юнит-тесты `nextTriggerAfter` на фиксированном `Clock`: все 7 дней недели × граничные времена 00:00 / 23:59 |
| Перепланирование после срабатывания | JVM-тест на Robolectric с `ShadowAlarmManager` |
| Отложенное переживает `rescheduleAll()` | JVM-тест на Robolectric: `snooze()` → `rescheduleAll()` → оба аларма на месте |
| Reboot | `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p <pkg>` |
| Doze | `adb shell dumpsys deviceidle force-idle`, затем `adb shell dumpsys alarm \| grep <pkg>` |
| Обновление приложения | переустановка с `-r`, проверка `MY_PACKAGE_REPLACED` |
| Смена часового пояса | Настройки эмулятора → Дата и время → выключить автоопределение пояса, выбрать Asia/Yekaterinburg (`setprop` требует root) |
| Отзыв точных алармов | Настройки → Будильники и напоминания → выключить, проверить деградацию на `setWindow` |
