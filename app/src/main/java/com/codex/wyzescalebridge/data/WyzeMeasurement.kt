package com.codex.wyzescalebridge.data

import java.time.Instant

data class WyzeMeasurement(
    val measuredAt: Instant,
    val weightKg: Double,
    val bodyFatPercent: Double?,
    val bmi: Double?,
)

fun WyzeMeasurement.weightKgToLb(): Double = weightKg / 0.45359237
