package com.systemobservatory.probe.telemetry

import com.systemobservatory.probe.model.Availability
import com.systemobservatory.probe.model.Classification
import com.systemobservatory.probe.model.TelemetryValue
import com.systemobservatory.probe.root.RootSession
import java.util.Locale

data class RootScan(
    val android: List<TelemetryValue> = emptyList(),
    val cpu: List<TelemetryValue> = emptyList(),
    val memory: List<TelemetryValue> = emptyList(),
    val battery: List<TelemetryValue> = emptyList(),
    val thermal: List<TelemetryValue> = emptyList(),
    val raw: List<TelemetryValue> = emptyList()
)

/** Read-only source discovery through a UID-verified RootSession. */
object RootProbe {
    fun scan(session: RootSession): RootScan {
        val raw = mutableListOf<TelemetryValue>()
        fun record(value: TelemetryValue): TelemetryValue {
            if (value.availability == Availability.AVAILABLE) raw += value
            return value
        }
        fun file(path: String, name: String): TelemetryValue = record(read(session, path, name))
        val version = file("/proc/version", "Kernel version (root)")
        val cpuInfo = file("/proc/cpuinfo", "CPU info")
        val stat = file("/proc/stat", "CPU stat")
        val memInfo = file("/proc/meminfo", "Memory info")
        val uptime = file("/proc/uptime", "Kernel uptime")

        val cpu = mutableListOf(cpuInfo, stat)
        val cpuRoot = "/sys/devices/system/cpu"
        val coreNames = directories(session, cpuRoot).filter { it.matches(Regex("cpu[0-9]+")) }.sortedBy { it.removePrefix("cpu").toIntOrNull() ?: Int.MAX_VALUE }
        cpu += if (coreNames.isEmpty()) missing("CPU cores discovered", cpuRoot, reason = "No CPU directories discovered")
        else direct("CPU cores discovered", coreNames.size, source = cpuRoot, kind = Classification.DERIVED)
        val cpuFields = listOf("online", "cpufreq/scaling_cur_freq", "cpufreq/scaling_min_freq", "cpufreq/scaling_max_freq", "cpufreq/scaling_available_governors", "cpufreq/scaling_governor")
        for (core in coreNames) for (field in cpuFields) {
            val path = "$cpuRoot/$core/$field"
            val value = file(path, "$core $field")
            cpu += if (field.endsWith("_freq") && value.availability == Availability.AVAILABLE) value.copy(unit = "kHz") else value
        }
        val first = stat.rawValue?.lineSequence()?.firstOrNull()?.let(Parsers::cpuTotals)
        val second = try { Thread.sleep(250); Parsers.cpuTotals(session.readFile("/proc/stat").lineSequence().first()) } catch (_: Exception) { null }
        val usage = if (first != null && second != null) Parsers.cpuUsage(first, second) else null
        cpu += if (usage == null) missing("CPU usage", "/proc/stat")
        else TelemetryValue("CPU usage", "two /proc/stat samples", "%.1f".format(Locale.US, usage), "%", "/proc/stat", Classification.DERIVED)

        val thermal = mutableListOf<TelemetryValue>()
        val thermalRoot = "/sys/class/thermal"
        for (zone in directories(session, thermalRoot).filter { it.matches(Regex("thermal_zone[0-9]+")) }.sorted()) {
            val base = "$thermalRoot/$zone"
            val type = file("$base/type", "$zone type")
            val temp = read(session, "$base/temp", "$zone temperature")
            thermal += type
            val celsius = temp.rawValue?.let(Parsers::thermalCelsius)
            val normalized = if (celsius == null) temp else temp.copy(normalizedValue = celsius.toString(), unit = "°C", classification = Classification.DERIVED)
            thermal += record(normalized)
        }

        val battery = mutableListOf<TelemetryValue>()
        scanTree(session, "/sys/class/power_supply", listOf("type", "status", "capacity", "voltage_now", "current_now", "temp", "charge_now", "energy_now"), ::record, battery)
        scanTree(session, "/sys/class/devfreq", listOf("cur_freq", "min_freq", "max_freq", "governor", "available_governors"), ::record, cpu)
        return RootScan(android = listOf(version, uptime), cpu = cpu, memory = listOf(memInfo), battery = battery, thermal = thermal, raw = raw)
    }

    private fun directories(session: RootSession, path: String): List<String> = try { session.listDirectory(path) } catch (_: Exception) { emptyList() }

    private fun scanTree(session: RootSession, tree: String, fields: List<String>, record: (TelemetryValue) -> TelemetryValue, destination: MutableList<TelemetryValue>) {
        for (node in directories(session, tree)) for (field in fields) {
            val path = "$tree/$node/$field"
            val value = record(read(session, path, "$node $field"))
            if (value.availability == Availability.AVAILABLE) destination += value
        }
    }

    private fun read(session: RootSession, path: String, name: String): TelemetryValue = try {
        val raw = session.readFile(path).trim()
        if (raw.isEmpty()) missing(name, path, path, "Empty file")
        else TelemetryValue(name, raw, raw, null, path, Classification.DIRECT_KERNEL, path = path)
    } catch (e: Exception) { failure(name, path, e, path) }
}
