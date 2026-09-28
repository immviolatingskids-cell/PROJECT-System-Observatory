package com.systemobservatory.probe.telemetry

import android.content.Context
import com.systemobservatory.probe.model.ProbeReport

object ProbeCollector {
    fun collect(context: Context, root: RootResult? = null): ProbeReport {
        val (device, android) = DeviceProbe.collect()
        return ProbeReport(device = device, android = android, root = root?.status ?: RootProbe.initial(),
            battery = BatteryProbe.collect(context), cpu = root?.cpu?.takeIf { it.isNotEmpty() } ?: listOf(missing("CPU sysfs telemetry", "Request root to scan or root access was denied")),
            memory = MemoryProbe.collect(context), storage = StorageProbe.collect(context), network = NetworkProbe.collect(context),
            thermal = ThermalProbe.collect(context) + (root?.thermal ?: emptyList()), rawSources = root?.raw ?: emptyList())
    }
}
