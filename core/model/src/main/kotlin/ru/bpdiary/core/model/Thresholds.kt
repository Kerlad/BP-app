package ru.bpdiary.core.model

data class Thresholds(
    val highSys: Int = 135,
    val highDia: Int = 85,
    val lowSys: Int = 100,
    val lowDia: Int = 60,
    val pulseLow: Int = 50,
    val pulseHigh: Int = 90,
)
