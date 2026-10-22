package ru.bpdiary.core.designsystem

import androidx.compose.ui.graphics.Color
import ru.bpdiary.core.model.Direction

// Hex values are copied from design_handoff_bp_diary/README.md ("Design Tokens"); do not retouch them by eye.

object Peach {
    val light =
        BpColors(
            bg = Color(0xFFFAF3EC),
            sf = Color(0xFFFFFFFF),
            sf2 = Color(0xFFF4E8DE),
            ink = Color(0xFF2D2420),
            mut = Color(0xFF7B6B62),
            ac = Color(0xFFDD6B4A),
            aci = Color(0xFFFFFFFF),
            acs = Color(0xFFFADFD3),
            hi = Color(0xFFC9443A),
            hiS = Color(0xFFF8DCD7),
            ok = Color(0xFF3F8B5E),
            okS = Color(0xFFDCEEDF),
            lo = Color(0xFF3F72B5),
            loS = Color(0xFFDCE7F5),
            ln = Color(0xFFECDDD1),
        )
    val dark =
        BpColors(
            bg = Color(0xFF1C1613),
            sf = Color(0xFF271F1B),
            sf2 = Color(0xFF322823),
            ink = Color(0xFFF6ECE5),
            mut = Color(0xFFB3A197),
            ac = Color(0xFFF08766),
            aci = Color(0xFF2A130B),
            acs = Color(0xFF47291E),
            hi = Color(0xFFF17A6E),
            hiS = Color(0xFF4A2421),
            ok = Color(0xFF7FC79A),
            okS = Color(0xFF1F3A2A),
            lo = Color(0xFF86ADE4),
            loS = Color(0xFF1F2E44),
            ln = Color(0xFF3B302A),
        )
}

object Sage {
    val light =
        BpColors(
            bg = Color(0xFFF2F3EA),
            sf = Color(0xFFFFFFFC),
            sf2 = Color(0xFFE6E9DA),
            ink = Color(0xFF22291F),
            mut = Color(0xFF646D5B),
            ac = Color(0xFF557A47),
            aci = Color(0xFFFFFFFF),
            acs = Color(0xFFDAE5CC),
            hi = Color(0xFFBF5236),
            hiS = Color(0xFFF4DDD3),
            ok = Color(0xFF557A47),
            okS = Color(0xFFDAE5CC),
            lo = Color(0xFF3E7397),
            loS = Color(0xFFD8E6EE),
            ln = Color(0xFFDDE1CF),
        )
    val dark =
        BpColors(
            bg = Color(0xFF141812),
            sf = Color(0xFF1D231B),
            sf2 = Color(0xFF262E23),
            ink = Color(0xFFEAEFE3),
            mut = Color(0xFFA2AC97),
            ac = Color(0xFF9BC285),
            aci = Color(0xFF13200C),
            acs = Color(0xFF2C3B25),
            hi = Color(0xFFEE8A6E),
            hiS = Color(0xFF43261D),
            ok = Color(0xFF9BC285),
            okS = Color(0xFF2C3B25),
            lo = Color(0xFF8BB9D8),
            loS = Color(0xFF1C2D38),
            ln = Color(0xFF313A2C),
        )
}

object Plum {
    val light =
        BpColors(
            bg = Color(0xFFF7F1F3),
            sf = Color(0xFFFFFFFF),
            sf2 = Color(0xFFEFE3E8),
            ink = Color(0xFF2C2128),
            mut = Color(0xFF77656F),
            ac = Color(0xFF9A4E78),
            aci = Color(0xFFFFFFFF),
            acs = Color(0xFFF1DCE7),
            hi = Color(0xFFC4453F),
            hiS = Color(0xFFF6DAD8),
            ok = Color(0xFF3E8A6A),
            okS = Color(0xFFD9EDE3),
            lo = Color(0xFF4A6FB0),
            loS = Color(0xFFDEE5F3),
            ln = Color(0xFFEADCE2),
        )
    val dark =
        BpColors(
            bg = Color(0xFF1A1418),
            sf = Color(0xFF251D22),
            sf2 = Color(0xFF30262C),
            ink = Color(0xFFF4EAF0),
            mut = Color(0xFFB19FAA),
            ac = Color(0xFFE08CB8),
            aci = Color(0xFF2B1020),
            acs = Color(0xFF44283A),
            hi = Color(0xFFF07E77),
            hiS = Color(0xFF47232A),
            ok = Color(0xFF82C9A6),
            okS = Color(0xFF1F3A2F),
            lo = Color(0xFF8FAEE6),
            loS = Color(0xFF222D45),
            ln = Color(0xFF3A2E35),
        )
}

fun bpColors(
    direction: Direction,
    dark: Boolean,
): BpColors =
    when (direction) {
        Direction.PEACH -> if (dark) Peach.dark else Peach.light
        Direction.SAGE -> if (dark) Sage.dark else Sage.light
        Direction.PLUM -> if (dark) Plum.dark else Plum.light
    }
