package ru.bpdiary.domain

/** At most [max] chars, but never ending in the first half of a surrogate pair (an emoji cut in two). */
fun String.takeWholeChars(max: Int): String {
    val cut = take(max)
    return if (cut.length < length && cut.lastOrNull()?.isHighSurrogate() == true) cut.dropLast(1) else cut
}
