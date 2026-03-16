package com.codex.wyzescalebridge.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WyzeCsvParserTest {
    private val parser = WyzeCsvParser()

    @Test
    fun parsesMetricWyzeExport() {
        val csv = """
            Date,Weight(kg),Body Fat(%),BMI
            2026-03-14 07:30:00,82.4,19.5,24.9
        """.trimIndent()

        val result = parser.parse(csv)

        assertEquals(1, result.size)
        assertEquals(82.4, result.first().weightKg, 0.001)
        assertEquals(19.5, result.first().bodyFatPercent ?: 0.0, 0.001)
        assertNotNull(result.first().measuredAt)
    }

    @Test
    fun convertsPoundsToKilograms() {
        val csv = """
            Date,Weight(lb),Body Fat(%)
            3/14/2026 7:30 AM,180.0,21.2
        """.trimIndent()

        val result = parser.parse(csv)

        assertEquals(1, result.size)
        assertEquals(81.6466, result.first().weightKg, 0.01)
        assertEquals(21.2, result.first().bodyFatPercent ?: 0.0, 0.001)
    }

    @Test
    fun rejectsNonWyzeHeaders() {
        val csv = """
            Name,Value
            weight,180
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(csv)
        }
    }

    @Test
    fun parsesQuotedFieldsWithEscapedQuotes() {
        val csv = "Date,Weight(lb),Note\n\"3/14/2026 7:30 AM\",\"180.0\",\"Said \"\"steady\"\"\""

        val result = parser.parse(csv)

        assertEquals(1, result.size)
        assertEquals(81.6466, result.first().weightKg, 0.01)
    }
}
