package com.systemobservatory.probe.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

enum class Classification { DIRECT_API, DIRECT_KERNEL, DERIVED, ESTIMATED, UNAVAILABLE, ERROR }
enum class Availability { AVAILABLE, UNAVAILABLE, ERROR }

data class TelemetryValue(
    val name: String,
    val rawValue: String? = null,
    val normalizedValue: String? = null,
    val unit: String? = null,
    val source: String,
    val classification: Classification,
    val timestamp: String = Instant.now().toString(),
    val availability: Availability = when (classification) {
        Classification.UNAVAILABLE -> Availability.UNAVAILABLE
        Classification.ERROR -> Availability.ERROR
        else -> Availability.AVAILABLE
    },
    val error: String? = null,
    val path: String? = null
) {
    fun json(): JSONObject = JSONObject().apply {
        put("name", name); put("raw_value", rawValue ?: JSONObject.NULL); put("normalized_value", normalizedValue ?: JSONObject.NULL)
        put("unit", unit ?: JSONObject.NULL); put("source", source); put("classification", classification.name)
        put("timestamp", timestamp); put("availability", availability.name); put("error", error ?: JSONObject.NULL); put("path", path ?: JSONObject.NULL)
    }
}

data class ProbeReport(
    val device: List<TelemetryValue> = emptyList(),
    val android: List<TelemetryValue> = emptyList(),
    val root: List<TelemetryValue> = emptyList(),
    val battery: List<TelemetryValue> = emptyList(),
    val cpu: List<TelemetryValue> = emptyList(),
    val memory: List<TelemetryValue> = emptyList(),
    val storage: List<TelemetryValue> = emptyList(),
    val network: List<TelemetryValue> = emptyList(),
    val thermal: List<TelemetryValue> = emptyList(),
    val rawSources: List<TelemetryValue> = emptyList()
) {
    fun json(): String = JSONObject().apply {
        put("device", device.arr()); put("android", android.arr()); put("root", root.arr())
        put("battery", battery.arr()); put("cpu", cpu.arr()); put("memory", memory.arr())
        put("storage", storage.arr()); put("network", network.arr()); put("thermal", thermal.arr())
        put("raw_sources", rawSources.arr())
    }.toString(2)
}

private fun List<TelemetryValue>.arr() = JSONArray().also { array -> forEach { array.put(it.json()) } }
