package ru.bpdiary.core.ui

import androidx.annotation.StringRes
import ru.bpdiary.app.core.ui.R
import ru.bpdiary.core.model.Tag

/** Russian label of a stored tag key (D-08). */
@StringRes
fun Tag.labelRes(): Int =
    when (this) {
        Tag.GOOD -> R.string.tag_good
        Tag.HEADACHE -> R.string.tag_headache
        Tag.DIZZINESS -> R.string.tag_dizziness
        Tag.WEAKNESS -> R.string.tag_weakness
        Tag.AFTER_MEDICINE -> R.string.tag_after_medicine
        Tag.AFTER_EXERCISE -> R.string.tag_after_exercise
    }
