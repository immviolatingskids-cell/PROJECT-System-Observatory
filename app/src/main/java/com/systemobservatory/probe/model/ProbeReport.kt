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
    val root: RootInfo = RootInfo(),
    val battery: List<TelemetryValue> = emptyList(),
    val cpu: List<TelemetryValue> = emptyList(),
    val memory: List<TelemetryValue> = emptyList(),
    val storage: List<TelemetryValue> = emptyList(),
    val network: List<TelemetryValue> = emptyList(),
    val thermal: List<TelemetryValue> = emptyList(),
    val rawSources: List<TelemetryValue> = emptyList()
) {
    fun json(): String = JSONObject().apply {
        put("device", device.arr()); put("android", android.arr()); put("root", root.json())
        put("battery", battery.arr()); put("cpu", cpu.arr()); put("memory", memory.arr())
        put("storage", storage.arr()); put("network", network.arr()); put("thermal", thermal.arr())
        put("raw_sources", rawSources.arr())
    }.toString(2)
}

private fun List<TelemetryValue>.arr() = JSONArray().also { array -> forEach { array.put(it.json()) } }

enum class RootState { NOT_CHECKED, SU_AVAILABLE, REQUESTING, GRANTED, DENIED, ERROR }

data class RootInfo(
    val suAvailable: Boolean? = null,
    val requestAttempted: Boolean = false,
    val state: RootState = RootState.NOT_CHECKED,
    val granted: Boolean = false,
    val uid: Int? = null,
    val provider: String? = null,
    val version: String? = null,
    val sessionActive: Boolean = false,
    val error: String? = null,
    val timestamp: String = Instant.now().toString()
) {
    fun json(): JSONObject = JSONObject().apply {
        put("su_available", suAvailable ?: JSONObject.NULL)
        put("request_attempted", requestAttempted)
        put("status", state.name)
        put("granted", granted)
        put("uid", uid ?: JSONObject.NULL)
        put("provider", provider ?: JSONObject.NULL)
        put("version", version ?: JSONObject.NULL)
        put("session_active", sessionActive)
        put("error", error ?: JSONObject.NULL)
        put("timestamp", timestamp)
    }

    fun rows(): List<TelemetryValue> = listOf(
        TelemetryValue("su binary", suAvailable?.toString(), when (suAvailable) { true -> "FOUND"; false -> "NOT FOUND"; null -> "UNKNOWN" }, null, "PATH inspection", if (suAvailable == null) Classification.UNAVAILABLE else Classification.DIRECT_API),
        TelemetryValue("request", state.name, state.name, null, if (requestAttempted) "User initiated su shell" else "No root request made", Classification.DIRECT_API),
        TelemetryValue("Root granted", granted.toString(), if (granted) "YES" else "NO", null, "su shell / id -u", Classification.DIRECT_API),
        if (uid == null) TelemetryValue("UID", source = "su shell / id -u", classification = Classification.UNAVAILABLE) else TelemetryValue("UID", uid.toString(), uid.toString(), null, "su shell / id -u", Classification.DIRECT_KERNEL),
        if (provider == null) TelemetryValue("Root provider", source = "su -v", classification = Classification.UNAVAILABLE) else TelemetryValue("Root provider", provider, provider, null, "su -v", Classification.DIRECT_KERNEL),
        if (version == null) TelemetryValue("Root version", source = "su -v", classification = Classification.UNAVAILABLE) else TelemetryValue("Root version", version, version, null, "su -v", Classification.DIRECT_KERNEL),
        TelemetryValue("Session", sessionActive.toString(), if (sessionActive) "ACTIVE" else "INACTIVE", null, "RootSession", Classification.DIRECT_API),
        if (error == null) TelemetryValue("Last root error", source = "RootManager", classification = Classification.UNAVAILABLE) else TelemetryValue("Last root error", error, error, null, "RootManager", Classification.ERROR)
    )
}
