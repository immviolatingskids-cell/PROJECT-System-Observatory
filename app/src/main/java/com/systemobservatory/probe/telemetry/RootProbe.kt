package com.systemobservatory.probe.telemetry

import com.systemobservatory.probe.model.Classification
import com.systemobservatory.probe.model.TelemetryValue
import java.io.File

data class RootResult(val status: List<TelemetryValue>, val cpu: List<TelemetryValue>, val thermal: List<TelemetryValue>, val raw: List<TelemetryValue>)

object RootProbe {
    fun initial(): List<TelemetryValue> = listOf(
        direct("Root installed", if (System.getenv("PATH")?.split(':')?.any { File(it, "su").exists() } == true) "YES" else "UNKNOWN", source = "PATH su inspection"),
        direct("Root granted", "NO", source = "No root request made"),
        missing("Root implementation/version", "Root not requested")
    )

    fun scan(): RootResult {
        val id = try { RootShell.command("id") } catch (e: Exception) {
            return RootResult(listOf(direct("Root installed", "UNKNOWN", source = "su attempt"), direct("Root granted", "NO", source = "su -c id"), failure("Root access", "su -c id", e)), emptyList(), emptyList(), emptyList())
        }
        if (!Regex("(?:^|[ (])uid=0(?:\\(|\\s)").containsMatchIn(id)) {
            return RootResult(listOf(direct("Root installed", "YES", source = "su -c id"), direct("Root granted", "NO", source = "su -c id"), direct("Identity", id, source = "su -c id")), emptyList(), emptyList(), emptyList())
        }
        val version = try { RootShell.command("su -v").take(120) } catch (_: Exception) { null }
        val status = listOf(direct("Root installed", "YES", source = "su -c id"), direct("Root granted", "YES", source = "su -c id"), direct("Root implementation/version", version, source = "su -v"))
        val raw = mutableListOf<TelemetryValue>()
        listOf("/proc/meminfo", "/proc/uptime", "/proc/stat", "/proc/cpuinfo").forEach { raw += readKernel(it, it.substringAfterLast('/'), true) }
        val cpu = mutableListOf<TelemetryValue>()
        val cores = File("/sys/devices/system/cpu").listFiles()?.filter { it.name.matches(Regex("cpu[0-9]+")) }?.sortedBy { it.name } ?: emptyList()
        cpu += direct("CPU cores discovered", cores.size, source = "/sys/devices/system/cpu/", kind = Classification.DERIVED)
        for (core in cores) {
            val base = core.absolutePath
            val fields = listOf("online", "cpufreq/scaling_cur_freq", "cpufreq/scaling_min_freq", "cpufreq/scaling_max_freq", "cpufreq/scaling_available_governors", "cpufreq/scaling_governor")
            for (field in fields) {
                val path = "$base/$field"
                val entry = if (field == "online" && core.name == "cpu0" && !File(path).exists()) direct("${core.name} online", "1", source = "Kernel CPU0 convention", kind = Classification.DERIVED) else readKernel(path, "${core.name} $field", true)
                cpu += entry; raw += entry
            }
        }
        val first = raw.firstOrNull { it.path == "/proc/stat" }?.rawValue?.lineSequence()?.firstOrNull()?.let(Parsers::cpuTotals)
        val second = try { Thread.sleep(250); Parsers.cpuTotals(RootShell.read("/proc/stat").lineSequence().first()) } catch (_: Exception) { null }
        val usage = if (first != null && second != null) Parsers.cpuUsage(first, second) else null
        cpu += if (usage != null) TelemetryValue("CPU usage", "two /proc/stat samples", "%.1f".format(java.util.Locale.US, usage), "%", "/proc/stat", Classification.DERIVED) else missing("CPU usage", "/proc/stat")
        val thermal = mutableListOf<TelemetryValue>()
        val zones = File("/sys/class/thermal").listFiles()?.filter { it.name.startsWith("thermal_zone") }?.sortedBy { it.name } ?: emptyList()
        for (zone in zones) {
            val type = readKernel("${zone.absolutePath}/type", "${zone.name} type", true)
            val temp = readKernel("${zone.absolutePath}/temp", "${zone.name} temperature", true)
            raw += type; raw += temp
            thermal += type
            val celsius = temp.rawValue?.let(Parsers::thermalCelsius)
            thermal += if (celsius == null) temp else temp.copy(normalizedValue = celsius.toString(), unit = "°C", classification = Classification.DERIVED)
        }
        val trees = listOf("/sys/class/power_supply" to listOf("type", "status", "capacity", "voltage_now", "current_now", "temp", "charge_now", "energy_now"),
            "/sys/class/devfreq" to listOf("cur_freq", "min_freq", "max_freq", "governor", "available_governors"))
        for ((tree, fields) in trees) {
            val nodes = File(tree).listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
            for (node in nodes) for (field in fields) {
                val path = "${node.absolutePath}/$field"
                if (File(path).exists()) raw += readKernel(path, "${node.name} $field", true)
            }
        }
        return RootResult(status, cpu, thermal, raw)
    }
}
