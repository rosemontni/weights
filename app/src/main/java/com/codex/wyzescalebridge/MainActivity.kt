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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WyzeScaleBridgeTheme {
                WyzeScaleBridgeApp()
            }
        }
    }
}

@Composable
private fun WyzeScaleBridgeApp() {
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

    var status by remember { mutableStateOf("Import a Wyze Scale CSV export to begin.") }
    var isSyncing by remember { mutableStateOf(false) }
    var healthAvailability by remember { mutableStateOf(healthConnectStatus(context)) }

    suspend fun syncImportedData() {
        isSyncing = true
        status = "Writing ${importedMeasurements.size} measurements to Health Connect..."
        status = when (val result = healthWriter.write(importedMeasurements)) {
            is HealthConnectWriter.SyncResult.Success ->
                "Synced ${result.recordsWritten} Health Connect record(s). Garmin Connect still will not auto-ingest them."
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
        healthAvailability = healthConnectStatus(context)
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
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
                LimitationsCard()
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
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Garmin reality check", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "This app stops at Health Connect because Garmin Connect does not provide a normal consumer API for body-weight imports from third-party Android apps.",
            )
            Text(
                text = "If you want true Garmin auto-sync later, we would need to gamble on a private Garmin endpoint or get access to a Garmin partner program, both of which are more fragile than this starter app.",
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
