package ru.bpdiary.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import ru.bpdiary.app.core.designsystem.R

/**
 * The fixed icon set: Material Symbols Rounded exported as local vectors (docs/04 §4.6).
 * The vectors carry no tint; colour them with `Icon(tint = ...)`.
 */
enum class BpIcon(
    @DrawableRes val res: Int,
) {
    Tune(R.drawable.ic_tune),
    Add(R.drawable.ic_add),
    Favorite(R.drawable.ic_favorite),

    /** Used in colour `hi`. */
    FavoriteFilled(R.drawable.ic_favorite_filled),
    Alarm(R.drawable.ic_alarm),
    ChevronRight(R.drawable.ic_chevron_right),
    Close(R.drawable.ic_close),
    Schedule(R.drawable.ic_schedule),
    Backspace(R.drawable.ic_backspace),
    ArrowForward(R.drawable.ic_arrow_forward),
    Check(R.drawable.ic_check),
    Monitoring(R.drawable.ic_monitoring),
    MenuBook(R.drawable.ic_menu_book),
    Home(R.drawable.ic_home),
    Notifications(R.drawable.ic_notifications),
    AddAlarm(R.drawable.ic_add_alarm),
    Remove(R.drawable.ic_remove),
    ArrowBack(R.drawable.ic_arrow_back),
    DarkMode(R.drawable.ic_dark_mode),
    CheckCircle(R.drawable.ic_check_circle),
}

@Composable
fun BpIcon.painter(): Painter = painterResource(res)
