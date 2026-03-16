package com.codex.wyzescalebridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.codex.wyzescalebridge.data.HealthConnectWriter
import com.codex.wyzescalebridge.data.WyzeCsvParser
import com.codex.wyzescalebridge.data.WyzeMeasurement
import com.codex.wyzescalebridge.data.weightKgToLb
import com.codex.wyzescalebridge.ui.theme.WyzeScaleBridgeTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.Instant
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val demoScenario = if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            DemoScenario.fromIntent(intent)
        } else {
            null
        }
        enableEdgeToEdge()
        setContent {
            WyzeScaleBridgeTheme {
                WyzeScaleBridgeApp(demoScenario = demoScenario)
            }
        }
    }
}

@Composable
private fun WyzeScaleBridgeApp(demoScenario: DemoScenario? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val parser = remember { WyzeCsvParser() }
    val importedMeasurements = remember { mutableStateListOf<WyzeMeasurement>() }
    val healthWriter = remember { HealthConnectWriter(context) }
    val permissions = remember {
        setOf(
            HealthPermission.getWritePermission(WeightRecord::class),
            HealthPermission.getWritePermission(BodyFatRecord::class),
        )
    }

    var status by remember { mutableStateOf(demoScenario?.status ?: "Import a Wyze Scale CSV export to begin.") }
    var isSyncing by remember { mutableStateOf(false) }
    var healthAvailability by remember {
        mutableStateOf(demoScenario?.healthAvailability ?: healthConnectStatus(context))
    }

    suspend fun syncImportedData() {
        if (demoScenario != null) {
            status = demoScenario.status
            return
        }
        isSyncing = true
        status = "Writing ${importedMeasurements.size} measurements to Health Connect..."
        status = when (val result = healthWriter.write(importedMeasurements)) {
            is HealthConnectWriter.SyncResult.Success ->
                "Synced ${result.recordsWritten} Health Connect record(s). Garmin Connect may read some Health Connect data on newer Android builds, but weight support is still unverified here."
            is HealthConnectWriter.SyncResult.Failure -> result.message
        }
        isSyncing = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { grantedPermissions ->
        scope.launch {
            if (grantedPermissions.containsAll(permissions)) {
                syncImportedData()
            } else {
                snackbarHostState.showSnackbar("Health Connect permission was not granted.")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }

            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    parser.parse(reader.readText())
                }.orEmpty()
            }.onSuccess { parsed ->
                importedMeasurements.clear()
                importedMeasurements.addAll(parsed.sortedByDescending { it.measuredAt })
                status = if (parsed.isEmpty()) {
                    "The file loaded, but no Wyze measurements were detected."
                } else {
                    "Imported ${parsed.size} Wyze measurement(s)."
                }
            }.onFailure { error ->
                status = "Import failed: ${error.message ?: "unknown error"}"
            }
        }
    }

    LaunchedEffect(context) {
        if (demoScenario == null) {
            healthAvailability = healthConnectStatus(context)
        }
    }

    LaunchedEffect(demoScenario) {
        if (demoScenario != null) {
            importedMeasurements.clear()
            importedMeasurements.addAll(demoScenario.measurements.sortedByDescending { it.measuredAt })
            status = demoScenario.status
            healthAvailability = demoScenario.healthAvailability
        }
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && demoScenario == null) {
                healthAvailability = healthConnectStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HeroCard(
                    healthAvailability = healthAvailability,
                    status = status,
                    modeLabel = demoScenario?.modeLabel,
                    onImport = { importLauncher.launch(arrayOf("text/csv", "application/csv", "application/vnd.ms-excel", "text/comma-separated-values")) },
                    onSync = {
                        scope.launch {
                            if (importedMeasurements.isEmpty()) {
                                snackbarHostState.showSnackbar("Import a Wyze CSV file first.")
                                return@launch
                            }

                            if (healthAvailability != HealthAvailability.Available) {
                                openHealthConnect(context)
                                return@launch
                            }

                            val granted = healthWriter.grantedPermissions()
                            if (granted.containsAll(permissions)) {
                                syncImportedData()
                            } else {
                                permissionLauncher.launch(permissions)
                            }
                        }
                    },
                    onInstallHealthConnect = { openHealthConnect(context) },
                    syncing = isSyncing,
                )
            }

            if (importedMeasurements.isNotEmpty()) {
                item {
                    SummaryCard(importedMeasurements.first())
                }
            }

            item {
                LimitationsCard(demoScenario = demoScenario)
            }

            if (importedMeasurements.isNotEmpty()) {
                items(importedMeasurements.take(20)) { measurement ->
                    MeasurementCard(measurement)
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    healthAvailability: HealthAvailability,
    status: String,
    modeLabel: String?,
    onImport: () -> Unit,
    onSync: () -> Unit,
    onInstallHealthConnect: () -> Unit,
    syncing: Boolean,
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Wyze Scale Bridge",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Imports Wyze Scale CSV exports and writes weight plus body-fat into Health Connect on Android.",
                style = MaterialTheme.typography.bodyLarge,
            )
            modeLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "Health Connect status: ${healthAvailability.label}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onImport) {
                    Text("Import Wyze CSV")
                }
                Button(
                    onClick = onSync,
                    enabled = !syncing,
                ) {
                    Text(if (syncing) "Syncing..." else "Write to Health Connect")
                }
            }
            if (healthAvailability != HealthAvailability.Available) {
                TextButton(onClick = onInstallHealthConnect) {
                    Text("Open Health Connect setup")
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(latest: WyzeMeasurement) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Latest measurement", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "${latest.weightKg.format(1)} kg (${latest.weightKgToLb().format(1)} lb)",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Measured ${latest.measuredAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))}",
            )
            latest.bodyFatPercent?.let {
                Text("Body fat: ${it.format(1)}%")
            }
            latest.bmi?.let {
                Text("BMI: ${it.format(1)}")
            }
        }
    }
}

@Composable
private fun LimitationsCard() {
    LimitationsCard(demoScenario = null)
}

@Composable
private fun LimitationsCard(demoScenario: DemoScenario?) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Garmin reality check", style = MaterialTheme.typography.titleLarge)
            Text(
                text = demoScenario?.garminMessagePrimary
                    ?: "This app writes to Health Connect first. Garmin Connect now appears to support some Health Connect data on newer Android versions, but this project has not verified Garmin weight or body-fat ingestion yet.",
            )
            Text(
                text = demoScenario?.garminMessageSecondary
                    ?: "The safest current path is still Wyze export to this app to Health Connect. If Garmin reads weight from Health Connect on your device, that becomes the bridge. If not, direct Garmin sync still needs a more fragile private or partner integration path.",
            )
        }
    }
}

@Composable
private fun MeasurementCard(measurement: WyzeMeasurement) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = measurement.measuredAt
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                fontWeight = FontWeight.SemiBold,
            )
            Text("Weight: ${measurement.weightKg.format(1)} kg")
            measurement.bodyFatPercent?.let { Text("Body fat: ${it.format(1)}%") }
            measurement.bmi?.let { Text("BMI: ${it.format(1)}") }
        }
    }
}

private fun openHealthConnect(context: Context) {
    val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        val playStoreIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=com.google.android.apps.healthdata"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(playStoreIntent) }
    }
}

private fun healthConnectStatus(context: Context): HealthAvailability {
    val sdkStatus = HealthConnectClient.getSdkStatus(context)
    return when (sdkStatus) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.Available
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UpdateRequired
        else -> HealthAvailability.Unavailable
    }
}

private enum class HealthAvailability(val label: String) {
    Available("Available"),
    UpdateRequired("Install or update required"),
    Unavailable("Not supported on this device"),
}

private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)

private data class DemoScenario(
    val modeLabel: String,
    val status: String,
    val healthAvailability: HealthAvailability,
    val measurements: List<WyzeMeasurement>,
    val garminMessagePrimary: String,
    val garminMessageSecondary: String,
) {
    companion object {
        fun fromIntent(intent: Intent?): DemoScenario? {
            return when (intent?.getStringExtra("screenshot_scene")) {
                "overview" -> overview()
                "garmin" -> garmin()
                else -> null
            }
        }

        private fun overview(): DemoScenario {
            return DemoScenario(
                modeLabel = "Screenshot scene: focused import overview",
                status = "Imported 18 Wyze measurement(s). Ready to write the latest set into Health Connect.",
                healthAvailability = HealthAvailability.Available,
                measurements = sampleMeasurements(),
                garminMessagePrimary = "This bridge keeps the workflow focused: import a Wyze CSV, preview the measurements, and write only weight plus body fat into Health Connect.",
                garminMessageSecondary = "That is useful if you want a narrower permission surface than the direct Wyze Health Connect integration.",
            )
        }

        private fun garmin(): DemoScenario {
            return DemoScenario(
                modeLabel = "Screenshot scene: Health Connect to Garmin path",
                status = "Synced 27 Health Connect record(s). Garmin Connect may read some Health Connect data on supported Android setups, but weight support still needs device-level verification.",
                healthAvailability = HealthAvailability.Available,
                measurements = sampleMeasurements(),
                garminMessagePrimary = "Garmin Connect now appears to support some Health Connect data on newer Android versions, so Health Connect is the best current bridge point.",
                garminMessageSecondary = "This project still treats Garmin weight and body-fat ingestion as something to verify on your specific phone and Garmin app version.",
            )
        }

        private fun sampleMeasurements(): List<WyzeMeasurement> {
            return listOf(
                WyzeMeasurement(Instant.parse("2026-03-15T11:45:00Z"), 82.4, 19.5, 24.9),
                WyzeMeasurement(Instant.parse("2026-03-14T11:42:00Z"), 82.6, 19.6, 25.0),
                WyzeMeasurement(Instant.parse("2026-03-13T11:41:00Z"), 82.8, 19.8, 25.0),
                WyzeMeasurement(Instant.parse("2026-03-12T11:40:00Z"), 83.1, 19.9, 25.1),
                WyzeMeasurement(Instant.parse("2026-03-11T11:39:00Z"), 83.0, 20.0, 25.1),
                WyzeMeasurement(Instant.parse("2026-03-10T11:38:00Z"), 83.3, 20.1, 25.2),
                WyzeMeasurement(Instant.parse("2026-03-09T11:37:00Z"), 83.4, 20.1, 25.2),
                WyzeMeasurement(Instant.parse("2026-03-08T11:36:00Z"), 83.5, 20.2, 25.3),
                WyzeMeasurement(Instant.parse("2026-03-07T11:35:00Z"), 83.6, 20.3, 25.3),
                WyzeMeasurement(Instant.parse("2026-03-06T11:34:00Z"), 83.7, 20.3, 25.4),
                WyzeMeasurement(Instant.parse("2026-03-05T11:33:00Z"), 83.8, 20.4, 25.4),
                WyzeMeasurement(Instant.parse("2026-03-04T11:32:00Z"), 83.9, 20.4, 25.4),
                WyzeMeasurement(Instant.parse("2026-03-03T11:31:00Z"), 84.0, 20.5, 25.5),
                WyzeMeasurement(Instant.parse("2026-03-02T11:30:00Z"), 84.1, 20.5, 25.5),
                WyzeMeasurement(Instant.parse("2026-03-01T11:29:00Z"), 84.0, 20.5, 25.5),
                WyzeMeasurement(Instant.parse("2026-02-28T11:28:00Z"), 84.2, 20.6, 25.6),
                WyzeMeasurement(Instant.parse("2026-02-27T11:27:00Z"), 84.3, 20.7, 25.6),
                WyzeMeasurement(Instant.parse("2026-02-26T11:26:00Z"), 84.5, 20.8, 25.7),
            )
        }
    }
}
