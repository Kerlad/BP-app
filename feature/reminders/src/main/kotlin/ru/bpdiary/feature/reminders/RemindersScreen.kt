package ru.bpdiary.feature.reminders

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.bpdiary.app.feature.reminders.R
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.designsystem.painter
import ru.bpdiary.core.ui.BpSwitch

private const val DISABLED_ALPHA = 0.5f
private const val DASH_DP = 8
private const val GAP_DP = 6

/** How the notification banner button behaves: ask the system, or send the user to the app settings (2nd denial). */
enum class NotificationAsk { REQUEST, OPEN_SETTINGS }

/** Reminders list (handoff screen 5). Stateless; the bottom navigation comes with the shell in Step 10.1. */
@Composable
fun RemindersScreen(
    state: RemindersUiState,
    onEvent: (RemindersEvent) -> Unit,
    modifier: Modifier = Modifier,
    notificationAsk: NotificationAsk = NotificationAsk.REQUEST,
) {
    val colors = BpTheme.colors
    val texts = rememberRemindersTexts()
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bg)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.reminders_title),
            style = BpTheme.typography.screenTitle,
            color = colors.ink,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).semantics { heading() },
        )
        if (state.notificationsMissing) {
            Banner(
                text = stringResource(R.string.reminders_banner_notifications),
                action =
                    stringResource(
                        if (notificationAsk == NotificationAsk.REQUEST) {
                            R.string.reminders_banner_notifications_allow
                        } else {
                            R.string.reminders_banner_open_settings
                        },
                    ),
                onClick = { onEvent(RemindersEvent.NotificationsBannerClicked) },
            )
        }
        if (state.exactAlarmsMissing) {
            Banner(
                text = stringResource(R.string.reminders_banner_exact),
                action = stringResource(R.string.reminders_banner_exact_action),
                onClick = { onEvent(RemindersEvent.ExactAlarmsBannerClicked) },
            )
        }
        state.items?.let { items ->
            items.forEach { item -> ReminderCard(item, texts, onEvent) }
            AddButton { onEvent(RemindersEvent.AddClicked) }
        }
    }
}

@Composable
private fun Banner(
    text: String,
    action: String,
    onClick: () -> Unit,
) {
    val colors = BpTheme.colors
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BpTheme.shapes.card))
                .background(colors.acs)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, style = BpTheme.typography.body, color = colors.ink)
        Text(
            text = action,
            style = BpTheme.typography.secondary.copy(fontWeight = FontWeight.W700),
            color = colors.aci,
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .background(colors.ac)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

/** Time 32, name 15/600, days 13; the card opens the edit screen, the switch is a separate target. */
@Composable
private fun ReminderCard(
    item: ReminderItem,
    texts: RemindersTexts,
    onEvent: (RemindersEvent) -> Unit,
) {
    val colors = BpTheme.colors
    val time = texts.dates.time(item.time)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BpTheme.shapes.card))
                .background(colors.sf),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(role = Role.Button) { onEvent(RemindersEvent.ItemClicked(item.id)) }
                    .alpha(if (item.enabled) 1f else DISABLED_ALPHA)
                    .padding(start = 18.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
        ) {
            Text(time, style = BpTheme.typography.numReminder, color = colors.ink)
            Text(item.label, style = BpTheme.typography.reminderTitle, color = colors.ink)
            Text(texts.days(item.days), style = BpTheme.typography.secondary, color = colors.mut)
        }
        BpSwitch(
            checked = item.enabled,
            onCheckedChange = { onEvent(RemindersEvent.Toggled(item.id, it)) },
            modifier = Modifier.padding(end = 10.dp),
            contentDescription = stringResource(R.string.reminders_switch_description, item.label, time),
        )
    }
}

/** Dashed `ln` outline, text `ac` (handoff); the dash lengths follow the density like the chart's. */
@Composable
private fun AddButton(onClick: () -> Unit) {
    val colors = BpTheme.colors
    val outline = colors.ln
    val radius = BpTheme.shapes.card
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .clip(RoundedCornerShape(radius))
                .clickable(role = Role.Button, onClick = onClick)
                .drawBehind {
                    val stroke = 1.5.dp.toPx()
                    val dashes = PathEffect.dashPathEffect(floatArrayOf(DASH_DP.dp.toPx(), GAP_DP.dp.toPx()))
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(radius.toPx()),
                        style = Stroke(width = stroke, pathEffect = dashes),
                    )
                }.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = BpIcon.AddAlarm.painter(),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.ac),
            modifier = Modifier.size(26.dp),
        )
        Text(stringResource(R.string.reminders_add), style = BpTheme.typography.button, color = colors.ac)
    }
}
