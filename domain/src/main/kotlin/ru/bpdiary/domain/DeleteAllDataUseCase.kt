package ru.bpdiary.domain

/**
 * «Удалить все данные» (docs/05 §5.7, docs/09 §9.4): readings including the soft-deleted ones, reminders back to the
 * three defaults, settings and thresholds back to the first launch, every alarm cancelled (the snooze included).
 * The settings go last: only then the app counts as freshly installed, so an interrupted run can be repeated.
 * The default reminders are planned again, as after the first install.
 */
class DeleteAllDataUseCase(
    private val readings: ReadingRepository,
    private val reminders: ReminderRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke() {
        scheduler.cancelAll()
        readings.deleteAll()
        reminders.resetToDefaults()
        settings.clearAll()
        scheduler.rescheduleAll()
    }
}
