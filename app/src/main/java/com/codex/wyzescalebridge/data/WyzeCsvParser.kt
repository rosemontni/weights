package com.codex.wyzescalebridge.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class WyzeCsvParser {
    fun parse(csv: String): List<WyzeMeasurement> {
        val rows = parseCsvRows(csv)
            .filter { row -> row.any { it.isNotBlank() } }

        if (rows.size < 2) {
            return emptyList()
        }

        val headers = rows.first().map(::normalizeHeader)
        require(looksLikeWyzeExport(headers)) {
            "This file does not look like a Wyze Scale CSV export."
        }

        val measurements = rows.drop(1).mapNotNull { row ->
            val values = headers.mapIndexed { index, header ->
                header to row.getOrElse(index) { "" }
            }.toMap()
            parseMeasurement(values)
        }

        if (measurements.isEmpty()) {
            throw IllegalArgumentException("No valid Wyze measurements were found in the CSV.")
        }

        return measurements
    }

    private fun parseMeasurement(values: Map<String, String>): WyzeMeasurement? {
        val measuredAt = parseDateTime(values) ?: return null
        val weightEntry = values.entries.firstOrNull { it.key.startsWith("weight") } ?: return null
        val weightKg = parseWeight(weightEntry.key, weightEntry.value) ?: return null
        val bodyFat = parseDouble(findFirst(values, "bodyfat", "bodyfatpercent", "fat"))
        val bmi = parseDouble(findFirst(values, "bmi"))

        return WyzeMeasurement(
            measuredAt = measuredAt,
            weightKg = weightKg,
            bodyFatPercent = bodyFat,
            bmi = bmi,
        )
    }

    private fun parseDateTime(values: Map<String, String>): Instant? {
        val direct = findFirst(values, "datetime", "measuretime", "timestamp", "date")
        parseInstantLike(direct)?.let { return it }

        val date = findFirst(values, "date")
        val time = findFirst(values, "time")
        if (!date.isNullOrBlank()) {
            val localDate = parseDate(date) ?: return null
            val localTime = parseTime(time) ?: LocalTime.MIDNIGHT
            return LocalDateTime.of(localDate, localTime)
                .atZone(ZoneId.systemDefault())
                .toInstant()
        }

        return null
    }

    private fun parseWeight(header: String, value: String): Double? {
        val parsed = parseDouble(value) ?: return null
        return if (header.contains("lb")) parsed * 0.45359237 else parsed
    }

    private fun parseDouble(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        val cleaned = value
            .replace("%", "")
            .replace("kg", "", ignoreCase = true)
            .replace("lb", "", ignoreCase = true)
            .replace(",", "")
            .trim()
        return cleaned.toDoubleOrNull()
    }

    private fun parseInstantLike(value: String?): Instant? {
        if (value.isNullOrBlank()) return null

        val formatters = listOf(
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US),
            DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US),
            DateTimeFormatter.ofPattern("M/d/yyyy h:mm a", Locale.US),
            DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss", Locale.US),
            DateTimeFormatter.ofPattern("M/d/yyyy H:mm", Locale.US),
        )

        for (formatter in formatters) {
            try {
                return LocalDateTime.parse(value.trim(), formatter)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            } catch (_: DateTimeParseException) {
            }
        }

        return runCatching { Instant.parse(value.trim()) }.getOrNull()
    }

    private fun parseDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        val formatters = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US),
            DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US),
        )
        for (formatter in formatters) {
            try {
                return LocalDate.parse(value.trim(), formatter)
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    private fun parseTime(value: String?): LocalTime? {
        if (value.isNullOrBlank()) return null
        val formatters = listOf(
            DateTimeFormatter.ISO_LOCAL_TIME,
            DateTimeFormatter.ofPattern("H:mm:ss", Locale.US),
            DateTimeFormatter.ofPattern("H:mm", Locale.US),
            DateTimeFormatter.ofPattern("h:mm:ss a", Locale.US),
            DateTimeFormatter.ofPattern("h:mm a", Locale.US),
        )
        for (formatter in formatters) {
            try {
                return LocalTime.parse(value.trim(), formatter)
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    private fun findFirst(values: Map<String, String>, vararg keys: String): String? {
        return keys.asSequence().mapNotNull { key -> values[key] }.firstOrNull()
    }

    private fun normalizeHeader(header: String): String {
        return header
            .lowercase(Locale.US)
            .replace("\"", "")
            .replace("(", "")
            .replace(")", "")
            .replace("%", "percent")
            .replace("\\s+".toRegex(), "")
    }

    private fun looksLikeWyzeExport(headers: List<String>): Boolean {
        val hasDate = headers.any { it in setOf("date", "datetime", "measuretime", "timestamp") }
        val hasWeight = headers.any { it.startsWith("weight") }
        return hasDate && hasWeight
    }

    private fun parseCsvRows(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        fun finishField() {
            currentRow += field.toString().trim()
            field.clear()
        }

        fun finishRow() {
            finishField()
            rows += currentRow.toList()
            currentRow.clear()
        }

        while (index < csv.length) {
            val char = csv[index]
            when {
                char == '"' && inQuotes && index + 1 < csv.length && csv[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> finishField()
                char == '\r' && !inQuotes -> {
                    finishRow()
                    if (index + 1 < csv.length && csv[index + 1] == '\n') {
                        index++
                    }
                }
                char == '\n' && !inQuotes -> finishRow()
                else -> field.append(char)
            }
            index++
        }

        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            finishRow()
        }

        return rows
    }
}
