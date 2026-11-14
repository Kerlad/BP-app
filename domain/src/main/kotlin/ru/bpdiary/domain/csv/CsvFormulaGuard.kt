package ru.bpdiary.domain.csv

private const val APOSTROPHE = '\''
private const val FORMULA_SIGNS = "=+-@"

private fun String.startsLikeFormula(): Boolean =
    trimStart(APOSTROPHE).firstOrNull()?.let { it in FORMULA_SIGNS } == true

/**
 * Excel and Sheets read a cell that starts with `=`, `+`, `-` or `@` as a formula. The export puts one apostrophe in
 * front of such a comment (also in front of one that already starts with apostrophes and then a sign, so that the
 * import can always take exactly one off).
 */
internal fun String.withFormulaGuard(): String = if (startsLikeFormula()) APOSTROPHE + this else this

/** The reverse of [withFormulaGuard]. */
internal fun String.withoutFormulaGuard(): String =
    if (startsWith(APOSTROPHE) && startsLikeFormula()) substring(1) else this
