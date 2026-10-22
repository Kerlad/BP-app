package ru.bpdiary.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.bpdiary.core.model.Direction

@Composable
private fun TypographySample(direction: Direction) {
    val t = BpTheme.typography
    val c = BpTheme.colors
    val rows: List<Pair<String, TextStyle>> =
        listOf(
            "Дневник давления" to t.screenTitle,
            "Доброе утро" to t.greeting,
            "Напоминания" to t.panelTitle,
            "Последнее измерение" to t.cardTitle,
            "Записать давление" to t.button,
            "Основной текст 15" to t.body,
            "Вторичный текст 14" to t.secondary,
            "Подпись 13" to t.caption,
            "Мелкая подпись 12" to t.micro,
            "мм рт. ст." to t.unit,
            "7 8 9" to t.key,
            "Утреннее измерение" to t.reminderTitle,
            "Повышенное" to t.chip,
            "Главная" to t.navActive,
            "Дневник" to t.navInactive,
            "/" to t.slash,
            "128/82" to t.numHero,
            "128/82" to t.numEntry,
            "08:00" to t.numReminder,
            "133/85" to t.numStat,
            "128/82" to t.numRow,
        )
    Column(
        modifier = Modifier.background(c.bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(direction.name, style = t.micro, color = c.mut)
        rows.forEach { (text, style) -> Text(text, style = style, color = c.ink) }
    }
}

@Preview(name = "Sage light", showBackground = true, heightDp = 900)
@Composable
private fun TypographySageLight() = BpTheme(Direction.SAGE, dark = false) { TypographySample(Direction.SAGE) }

@Preview(name = "Sage dark", showBackground = true, heightDp = 900)
@Composable
private fun TypographySageDark() = BpTheme(Direction.SAGE, dark = true) { TypographySample(Direction.SAGE) }

@Preview(name = "Peach light", showBackground = true, heightDp = 900)
@Composable
private fun TypographyPeachLight() = BpTheme(Direction.PEACH, dark = false) { TypographySample(Direction.PEACH) }

@Preview(name = "Plum dark", showBackground = true, heightDp = 900)
@Composable
private fun TypographyPlumDark() = BpTheme(Direction.PLUM, dark = true) { TypographySample(Direction.PLUM) }
