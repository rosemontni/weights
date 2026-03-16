package com.codex.wyzescalebridge.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import java.time.ZoneId
import kotlin.math.roundToLong

class HealthConnectWriter(context: Context) {
    private val appContext = context.applicationContext
    private val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(appContext)
    }

    suspend fun grantedPermissions(): Set<String> = client.permissionController.getGrantedPermissions()

    suspend fun write(measurements: List<WyzeMeasurement>): SyncResult {
        return runCatching {
            val records = measurements.flatMap { measurement ->
                buildList {
                    add(
                        WeightRecord(
                            time = measurement.measuredAt,
                            zoneOffset = ZoneId.systemDefault().rules.getOffset(measurement.measuredAt),
                            weight = Mass.kilograms(measurement.weightKg),
                            metadata = Metadata.manualEntry(
                                clientRecordId = measurement.clientRecordId("weight"),
                                clientRecordVersion = measurement.clientRecordVersion(),
                            ),
                        ),
                    )
                    measurement.bodyFatPercent?.let { bodyFat ->
                        add(
                            BodyFatRecord(
                                time = measurement.measuredAt,
                                zoneOffset = ZoneId.systemDefault().rules.getOffset(measurement.measuredAt),
                                percentage = Percentage(bodyFat),
                                metadata = Metadata.manualEntry(
                                    clientRecordId = measurement.clientRecordId("bodyFat"),
                                    clientRecordVersion = measurement.clientRecordVersion(),
                                ),
                            ),
                        )
                    }
                }
            }
            client.insertRecords(records)
            SyncResult.Success(records.size)
        }.getOrElse { error ->
            SyncResult.Failure(error.message ?: "Health Connect write failed.")
        }
    }

    sealed interface SyncResult {
        data class Success(val recordsWritten: Int) : SyncResult
        data class Failure(val message: String) : SyncResult
    }
}

private fun WyzeMeasurement.clientRecordId(kind: String): String {
    return "wyze-$kind-${measuredAt.toEpochMilli()}"
}

private fun WyzeMeasurement.clientRecordVersion(): Long {
    val bodyFatPart = ((bodyFatPercent ?: 0.0) * 10).roundToLong()
    val bmiPart = ((bmi ?: 0.0) * 10).roundToLong()
    return (weightKg * 1000).roundToLong() + bodyFatPart + bmiPart
}
