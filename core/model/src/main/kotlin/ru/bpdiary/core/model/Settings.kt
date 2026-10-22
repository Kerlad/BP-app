package ru.bpdiary.core.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Colour direction. Only SAGE is released (D-01); the others stay in code. */
enum class Direction { PEACH, SAGE, PLUM }

/** Version of the medical disclaimer the user has to accept; the screen is shown while the stored one is lower. */
const val DISCLAIMER_VERSION = 1
