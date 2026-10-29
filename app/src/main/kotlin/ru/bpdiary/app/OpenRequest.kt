package ru.bpdiary.app

import android.content.Intent
import ru.bpdiary.data.reminders.NotificationReminderNotifier

/**
 * The reminder notification opens the launcher activity with `open=entry_new` (D-14). An activity that is brought
 * back from the recent apps list gets the old intent again, which must not open Entry a second time.
 */
internal fun Intent.requestsNewEntry(): Boolean =
    getStringExtra(NotificationReminderNotifier.EXTRA_OPEN) == NotificationReminderNotifier.OPEN_ENTRY_NEW &&
        (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
