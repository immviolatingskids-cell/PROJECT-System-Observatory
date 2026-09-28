package com.systemobservatory.probe.telemetry

import android.content.Context
import com.systemobservatory.probe.model.ProbeReport
import com.systemobservatory.probe.model.RootInfo

object ProbeCollector {
    fun collect(context: Context, rootInfo: RootInfo, root: RootScan? = null): ProbeReport {
        val (device, android) = DeviceProbe.collect()
        return ProbeReport(device = device, android = android + (root?.android ?: emptyList()), root = rootInfo,
            battery = BatteryProbe.collect(context) + (root?.battery ?: emptyList()), cpu = root?.cpu?.takeIf { it.isNotEmpty() } ?: listOf(missing("CPU sysfs telemetry", "Request root to scan or root access was denied")),
            memory = MemoryProbe.collect(context) + (root?.memory ?: emptyList()), storage = StorageProbe.collect(context), network = NetworkProbe.collect(context),
            thermal = ThermalProbe.collect(context) + (root?.thermal ?: emptyList()), rawSources = root?.raw ?: emptyList())
    }
}
