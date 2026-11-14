package ru.bpdiary.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.bpdiary.app.feature.home.R
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.designsystem.painter
import ru.bpdiary.core.ui.BigReading
import ru.bpdiary.core.ui.BigReadingSize
import ru.bpdiary.core.ui.BpBanner
import ru.bpdiary.core.ui.BpCard
import ru.bpdiary.core.ui.BpPrimaryButton
import ru.bpdiary.core.ui.FixedScreenColumn
import ru.bpdiary.core.ui.PulseRow
import ru.bpdiary.core.ui.RoundIconButton
import ru.bpdiary.core.ui.StatusChip
import ru.bpdiary.core.ui.flexibleHeight
import ru.bpdiary.domain.NextReminderLabel
import ru.bpdiary.app.core.ui.R as CoreR

private val CardGap = 14.dp

/** Home (handoff screen 1). Stateless: everything comes from [state]. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val base = modifier.fillMaxSize().background(BpTheme.colors.bg)
    when (state) {
        HomeUiState.Loading -> Box(base)
        is HomeUiState.Content -> HomeContent(state, onEvent, base)
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Content,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier,
) {
    val texts = rememberHomeTexts()
    FixedScreenColumn(
        gap = CardGap,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Header(
            date = texts.dates.weekdayDayMonth(state.today),
            greeting = texts.greeting(state.greeting),
            onLevels = { onEvent(HomeEvent.LevelsClicked) },
            onSettings = { onEvent(HomeEvent.SettingsClicked) },
        )
        if (state.last != null) {
            LastReadingCard(state.last, texts) { onEvent(HomeEvent.LastReadingClicked(state.last.id)) }
        } else {
            EmptyCard()
        }
        BpPrimaryButton(stringResource(R.string.home_add_reading), { onEvent(HomeEvent.AddClicked) }, icon = BpIcon.Add)
        state.week?.let { WeekCard(it, texts, Modifier.flexibleHeight()) { onEvent(HomeEvent.WeekClicked) } }
        ReminderCard(state.nextReminder, texts) { onEvent(HomeEvent.ReminderClicked) }
        if (state.notificationsBanner) {
            BpBanner(
                text = stringResource(CoreR.string.banner_notifications),
                action = stringResource(CoreR.string.banner_notifications_allow),
                onClick = { onEvent(HomeEvent.ReminderClicked) },
            )
        }
    }
}

@Composable
private fun Header(
    date: String,
    greeting: String,
    onLevels: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = BpTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(date, style = BpTheme.typography.secondary, color = colors.mut)
            Text(
                text = greeting,
                style = BpTheme.typography.greeting,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            RoundIconButton(
                icon = BpIcon.Settings,
                description = stringResource(R.string.home_settings_description),
                onClick = onSettings,
                filled = true,
            )
            RoundIconButton(
                icon = BpIcon.Tune,
                description = stringResource(R.string.home_levels_description),
                onClick = onLevels,
                filled = true,
            )
        }
    }
}

@Composable
private fun LastReadingCard(
    last: LastReadingUi,
    texts: HomeTexts,
    onClick: () -> Unit,
) {
    val colors = BpTheme.colors
    BpCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Wraps the chip below the time at large font scales instead of breaking «Сегодня» mid-word.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = texts.dates.dayTime(last.dayLabel, last.time),
                    style = BpTheme.typography.secondary,
                    color = colors.mut,
                    modifier = Modifier.padding(end = 8.dp),
                )
                StatusChip(last.status)
            }
            BigReading(last.sys, last.dia, BigReadingSize.Hero)
            if (last.pulse != null) {
                PulseRow(last.pulse, last.pulseOutOfRange, caption = texts.pulseCaption(last.arm))
            } else {
                Text(texts.arm(last.arm), style = BpTheme.typography.body, color = colors.mut)
            }
        }
    }
}

@Composable
private fun EmptyCard() {
    BpCard {
        Text(stringResource(R.string.home_empty), style = BpTheme.typography.body, color = BpTheme.colors.ink)
    }
}

@Composable
private fun ReminderCard(
    next: NextReminderLabel?,
    texts: HomeTexts,
    onClick: () -> Unit,
) {
    val colors = BpTheme.colors
    BpCard(padding = 0.dp, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.acs),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = BpIcon.Alarm.painter(),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.ac),
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_reminder_title),
                    style = BpTheme.typography.caption,
                    color = colors.mut,
                )
                Text(texts.nextReminder(next), style = BpTheme.typography.cardTitle, color = colors.ink)
            }
            Image(
                painter = BpIcon.ChevronRight.painter(),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colors.mut),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
