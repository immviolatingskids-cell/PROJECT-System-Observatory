package com.systemobservatory.probe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.systemobservatory.probe.model.ProbeReport
import com.systemobservatory.probe.model.RootState
import com.systemobservatory.probe.model.TelemetryValue
import com.systemobservatory.probe.root.RootManager
import com.systemobservatory.probe.telemetry.ProbeCollector
import com.systemobservatory.probe.telemetry.RootProbe
import com.systemobservatory.probe.telemetry.RootScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val rootManager = RootManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ProbeScreen() }
    }

    override fun onDestroy() {
        rootManager.close()
        super.onDestroy()
    }

    @Composable
    private fun ProbeScreen() {
        var report by remember { mutableStateOf(ProbeReport()) }
        var rootScan by remember { mutableStateOf<RootScan?>(null) }
        var rootInfo by remember { mutableStateOf(rootManager.info) }
        var selected by remember { mutableStateOf("OVERVIEW") }
        var busy by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) scope.launch(Dispatchers.IO) {
                try { contentResolver.openOutputStream(uri)?.use { it.write(report.json().toByteArray(Charsets.UTF_8)) } }
                catch (e: Exception) { withContext(Dispatchers.Main) { message = "Export failed: ${e.message}" } }
            }
        }
        fun refresh() {
            scope.launch {
                busy = true
                try {
                    val scan = withContext(Dispatchers.IO) { rootManager.session?.takeIf { it.active }?.let(RootProbe::scan) }
                    rootScan = scan
                    rootInfo = rootManager.current()
                    report = withContext(Dispatchers.IO) { ProbeCollector.collect(this@MainActivity, rootInfo, scan) }
                }
                catch (e: Exception) { message = "Probe failed: ${e.message}" }
                busy = false
            }
        }
        LaunchedEffect(Unit) { refresh() }
        MaterialTheme {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
                Text("Telemetry Probe v0.2", style = MaterialTheme.typography.headlineSmall)
                Text("Local device discovery", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { refresh() }, enabled = !busy) { Text("Refresh") }
                    OutlinedButton(onClick = { export.launch("system-observatory-probe.json") }, enabled = !busy) { Text("Export Probe Report") }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (message != null) Text(message!!, color = MaterialTheme.colorScheme.error)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("OVERVIEW", "BATTERY", "CPU", "MEMORY", "THERMALS", "NETWORK", "RAW", "ROOT").forEach { tab ->
                        FilterChip(selected = selected == tab, onClick = { selected = tab }, label = { Text(tab) })
                    }
                }
                if (selected == "ROOT") {
                    Text("ROOT ACCESS", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = {
                        scope.launch {
                            busy = true
                            message = null
                            rootInfo = rootInfo.copy(state = RootState.REQUESTING, requestAttempted = true)
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    val acquired = if (rootManager.current().granted) rootManager.current() else rootManager.request()
                                    val scan = if (acquired.granted) rootManager.session?.let(RootProbe::scan) else null
                                    Triple(rootManager.current(), scan, acquired)
                                }
                                rootInfo = result.first
                                rootScan = result.second
                                report = withContext(Dispatchers.IO) { ProbeCollector.collect(this@MainActivity, rootInfo, rootScan) }
                            } catch (e: Exception) { message = "Root scan failed: ${e.message}" }
                            busy = false
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (rootInfo.granted) "RE-RUN ROOT PROBE" else "REQUEST ROOT ACCESS") }
                }
                val entries = when (selected) {
                    "OVERVIEW" -> report.device + report.android + report.storage
                    "BATTERY" -> report.battery
                    "CPU" -> report.cpu
                    "MEMORY" -> report.memory
                    "THERMALS" -> report.thermal
                    "NETWORK" -> report.network
                    "RAW" -> report.rawSources
                    else -> rootInfo.rows()
                }
                if (entries.isEmpty()) Text("No sources discovered yet.")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries) { TelemetryRow(it, selected == "RAW") }
                }
            }
        }
    }
}

@Composable
private fun TelemetryRow(value: TelemetryValue, collapsible: Boolean) {
    var expanded by remember(value.name, value.path) { mutableStateOf(!collapsible) }
    Card(Modifier.fillMaxWidth().clickable { if (collapsible) expanded = !expanded }) {
        Column(Modifier.padding(12.dp)) {
            Text(value.name, style = MaterialTheme.typography.titleMedium)
            Text(if (value.normalizedValue == null) value.availability.name else "${value.normalizedValue} ${value.unit ?: ""}", style = MaterialTheme.typography.headlineSmall)
            Text("${value.classification} · ${value.availability}", style = MaterialTheme.typography.labelMedium)
            if (expanded) {
                Text("Source: ${value.source}")
                value.path?.let { Text("Path: $it") }
                value.rawValue?.let { Text("Raw: $it") }
                Text("Updated: ${value.timestamp}")
                value.error?.let { Text("Detail: $it", color = MaterialTheme.colorScheme.error) }
            } else Text("Tap for source and raw value", style = MaterialTheme.typography.bodySmall)
        }
    }
}
