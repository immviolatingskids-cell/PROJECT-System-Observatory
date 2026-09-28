package com.systemobservatory.probe.telemetry

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.os.storage.StorageManager
import android.os.StatFs
import com.systemobservatory.probe.model.Classification
import com.systemobservatory.probe.model.TelemetryValue
import java.io.File

object DeviceProbe {
    fun collect(): Pair<List<TelemetryValue>, List<TelemetryValue>> {
        val device = listOf(
            direct("Manufacturer", Build.MANUFACTURER, source = "Build.MANUFACTURER"),
            direct("Model", Build.MODEL, source = "Build.MODEL"),
            direct("Device", Build.DEVICE, source = "Build.DEVICE"),
            direct("Product", Build.PRODUCT, source = "Build.PRODUCT")
        )
        val android = listOf(
            direct("Android version", Build.VERSION.RELEASE, source = "Build.VERSION.RELEASE"),
            direct("API level", Build.VERSION.SDK_INT, source = "Build.VERSION.SDK_INT"),
            direct("Build ID", Build.ID, source = "Build.ID"),
            direct("Security patch", Build.VERSION.SECURITY_PATCH, source = "Build.VERSION.SECURITY_PATCH"),
            readKernel("/proc/version", "Kernel version"),
            direct("Uptime", SystemClock.elapsedRealtime() / 1000.0, "s", "SystemClock.elapsedRealtime", Classification.DERIVED)
        )
        return device to android
    }
}

object BatteryProbe {
    fun collect(context: Context): List<TelemetryValue> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val manager = context.getSystemService(BatteryManager::class.java)
        if (intent == null) return listOf(missing("Battery broadcast", "ACTION_BATTERY_CHANGED"))
        fun extra(key: String): Int = intent.getIntExtra(key, Int.MIN_VALUE)
        fun number(name: String, key: String, unit: String?, scale: Double = 1.0): TelemetryValue {
            val raw = extra(key)
            return if (raw == Int.MIN_VALUE) missing(name, "ACTION_BATTERY_CHANGED/$key")
            else TelemetryValue(name, raw.toString(), (raw / scale).toString(), unit, "ACTION_BATTERY_CHANGED/$key", if (scale == 1.0) Classification.DIRECT_API else Classification.DERIVED)
        }
        fun property(name: String, id: Int, unit: String): TelemetryValue = try {
            val raw = manager.getLongProperty(id)
            if (raw == Long.MIN_VALUE || raw == Int.MIN_VALUE.toLong()) missing(name, "BatteryManager.getLongProperty($id)")
            else direct(name, raw, unit, "BatteryManager.getLongProperty($id)")
        } catch (e: Exception) { failure(name, "BatteryManager.getLongProperty($id)", e) }
        val level = extra(BatteryManager.EXTRA_LEVEL)
        val scale = extra(BatteryManager.EXTRA_SCALE)
        val percent = if (level >= 0 && scale > 0) TelemetryValue("Battery percentage", "$level/$scale", "${100.0 * level / scale}", "%", "ACTION_BATTERY_CHANGED/level,scale", Classification.DERIVED)
            else missing("Battery percentage", "ACTION_BATTERY_CHANGED/level,scale")
        val status = when (extra(BatteryManager.EXTRA_STATUS)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> null
        }
        val plugged = when (extra(BatteryManager.EXTRA_PLUGGED)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
            0 -> "None"
            else -> null
        }
        val health = when (extra(BatteryManager.EXTRA_HEALTH)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> null
        }
        return listOf(percent, direct("State", status, source = "ACTION_BATTERY_CHANGED/status"), direct("Power source", plugged, source = "ACTION_BATTERY_CHANGED/plugged"),
            number("Temperature", BatteryManager.EXTRA_TEMPERATURE, "°C", 10.0), number("Voltage", BatteryManager.EXTRA_VOLTAGE, "mV"),
            property("Current now", BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, "µA"), property("Average current", BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE, "µA"),
            property("Charge counter", BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER, "µAh"), property("Energy counter", BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER, "nWh"),
            direct("Health", health, source = "ACTION_BATTERY_CHANGED/health"), direct("Technology", intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY), source = "ACTION_BATTERY_CHANGED/technology"))
    }
}

object MemoryProbe {
    fun collect(context: Context): List<TelemetryValue> = try {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        listOf(direct("Total RAM", info.totalMem, "B", "ActivityManager.MemoryInfo.totalMem"),
            direct("Available RAM", info.availMem, "B", "ActivityManager.MemoryInfo.availMem"),
            direct("Low memory", info.lowMemory, source = "ActivityManager.MemoryInfo.lowMemory"))
    } catch (e: Exception) { listOf(failure("Memory", "ActivityManager.getMemoryInfo", e)) }
}

object StorageProbe {
    fun collect(context: Context): List<TelemetryValue> = try {
        val path = context.filesDir.absolutePath
        val fs = StatFs(path)
        val total = fs.totalBytes
        val available = fs.availableBytes
        listOf(direct("Total internal data filesystem", total, "B", "StatFs($path).totalBytes"),
            direct("Available internal data filesystem", available, "B", "StatFs($path).availableBytes"),
            TelemetryValue("Used internal data filesystem", "$total-$available", (total - available).toString(), "B", "StatFs($path)", Classification.DERIVED))
    } catch (e: Exception) { listOf(failure("Internal storage", "StatFs", e)) }
}

object NetworkProbe {
    fun collect(context: Context): List<TelemetryValue> = try {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork
        val caps = network?.let(manager::getNetworkCapabilities)
        if (caps == null) listOf(missing("Active network", "ConnectivityManager.activeNetwork"))
        else {
            val types = listOf(NetworkCapabilities.TRANSPORT_WIFI to "Wi-Fi", NetworkCapabilities.TRANSPORT_CELLULAR to "Mobile", NetworkCapabilities.TRANSPORT_ETHERNET to "Ethernet", NetworkCapabilities.TRANSPORT_VPN to "VPN")
            buildList {
                add(direct("Active transport", types.filter { caps.hasTransport(it.first) }.joinToString(", ") { it.second }.ifBlank { "Other" }, source = "NetworkCapabilities"))
                types.forEach { (type, label) -> add(direct("$label active", caps.hasTransport(type), source = "NetworkCapabilities.hasTransport($type)")) }
                add(direct("Metered", manager.isActiveNetworkMetered, source = "ConnectivityManager.isActiveNetworkMetered"))
                add(if (caps.linkDownstreamBandwidthKbps > 0) direct("Downstream bandwidth estimate", caps.linkDownstreamBandwidthKbps, "Kbps", "NetworkCapabilities.linkDownstreamBandwidthKbps", Classification.ESTIMATED) else missing("Downstream bandwidth estimate", "NetworkCapabilities"))
                add(if (caps.linkUpstreamBandwidthKbps > 0) direct("Upstream bandwidth estimate", caps.linkUpstreamBandwidthKbps, "Kbps", "NetworkCapabilities.linkUpstreamBandwidthKbps", Classification.ESTIMATED) else missing("Upstream bandwidth estimate", "NetworkCapabilities"))
            }
        }
    } catch (e: Exception) { listOf(failure("Network", "ConnectivityManager", e)) }
}

object ThermalProbe {
    fun collect(context: Context): List<TelemetryValue> = try {
        val manager = context.getSystemService(PowerManager::class.java)
        val status = manager.currentThermalStatus
        val headroom = manager.getThermalHeadroom(10)
        listOf(direct("Thermal status", status, source = "PowerManager.currentThermalStatus"),
            if (headroom.isFinite() && headroom >= 0f) direct("Thermal headroom", headroom, source = "PowerManager.getThermalHeadroom(10)") else missing("Thermal headroom", "PowerManager.getThermalHeadroom(10)"),
            missing("CPU headroom", "No supported verified source discovered"), missing("GPU headroom", "No supported verified source discovered"))
    } catch (e: Exception) { listOf(failure("Thermal", "PowerManager", e)) }
}
