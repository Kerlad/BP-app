package ru.bpdiary.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import ru.bpdiary.app.core.designsystem.R
import ru.bpdiary.core.model.Direction

/**
 * Golos Text, one variable font file bundled in the APK (D-15): no Downloadable Fonts, no network.
 * The weight axis runs 400..900; the four faces below pick the weights the design uses.
 */
val GolosText: FontFamily =
    FontFamily(
        listOf(FontWeight.W400, FontWeight.W500, FontWeight.W600, FontWeight.W700).map { weight ->
            Font(
                resId = R.font.golos_text,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        },
    )

/** Peach and Plum keep their own family reference, but their font files are not bundled: they fall back to Golos. */
val Nunito: FontFamily = GolosText
val Onest: FontFamily = GolosText

fun fontFamilyFor(direction: Direction): FontFamily =
    when (direction) {
        Direction.PEACH -> Nunito
        Direction.SAGE -> GolosText
        Direction.PLUM -> Onest
    }

private const val NUMBER_WEIGHT_PEACH = 800
private const val NUMBER_WEIGHT_OTHER = 700

/** Weight of the blood pressure digits (`nw` in the handoff). */
fun numberWeight(direction: Direction): FontWeight =
    FontWeight(if (direction == Direction.PEACH) NUMBER_WEIGHT_PEACH else NUMBER_WEIGHT_OTHER)

/** All sizes are in `sp` so the app follows the system font scale. */
@Immutable
data class BpTypography(
    /** 28 / 700 */
    val screenTitle: TextStyle,
    /** 24 / 700 */
    val greeting: TextStyle,
    /** 20 / 700 */
    val panelTitle: TextStyle,
    /** 16 / 700 */
    val cardTitle: TextStyle,
    /** 18 / 700 */
    val button: TextStyle,
    /** 15 / 400 */
    val body: TextStyle,
    /** 14 / 400 */
    val secondary: TextStyle,
    /** 13 / 400 */
    val caption: TextStyle,
    /** 12 / 400 */
    val micro: TextStyle,
    /** 14 / 500, "мм рт. ст." on Home */
    val unit: TextStyle,
    /** 24 / 600, keypad digits */
    val key: TextStyle,
    /** 15 / 600, reminder name */
    val reminderTitle: TextStyle,
    /** 13 / 700, status chip */
    val chip: TextStyle,
    /** 12 / 700, label of the active navigation item */
    val navActive: TextStyle,
    /** 12 / 500 */
    val navInactive: TextStyle,
    /** 40 / 400, the slash in "128/82" on Home, colour `mut` */
    val slash: TextStyle,
    /** 62 / nw, letter spacing -0.03em */
    val numHero: TextStyle,
    /** 36 / nw */
    val numEntry: TextStyle,
    /** 32 / nw */
    val numReminder: TextStyle,
    /** 26 / nw */
    val numStat: TextStyle,
    /** 22 / nw */
    val numRow: TextStyle,
)

private const val HERO_LETTER_SPACING = -0.03f

fun bpTypography(direction: Direction): BpTypography {
    val family = fontFamilyFor(direction)
    val nw = numberWeight(direction)

    fun style(
        size: Int,
        weight: FontWeight,
    ) = TextStyle(fontFamily = family, fontSize = size.sp, fontWeight = weight)

    return BpTypography(
        screenTitle = style(28, FontWeight.W700),
        greeting = style(24, FontWeight.W700),
        panelTitle = style(20, FontWeight.W700),
        cardTitle = style(16, FontWeight.W700),
        button = style(18, FontWeight.W700),
        body = style(15, FontWeight.W400),
        secondary = style(14, FontWeight.W400),
        caption = style(13, FontWeight.W400),
        micro = style(12, FontWeight.W400),
        unit = style(14, FontWeight.W500),
        key = style(24, FontWeight.W600),
        reminderTitle = style(15, FontWeight.W600),
        chip = style(13, FontWeight.W700),
        navActive = style(12, FontWeight.W700),
        navInactive = style(12, FontWeight.W500),
        slash = style(40, FontWeight.W400),
        numHero = style(62, nw).copy(letterSpacing = HERO_LETTER_SPACING.em),
        numEntry = style(36, nw),
        numReminder = style(32, nw),
        numStat = style(26, nw),
        numRow = style(22, nw),
    )
}
