package com.systemobservatory.probe.telemetry

import com.systemobservatory.probe.model.Classification
import com.systemobservatory.probe.model.TelemetryValue
import java.io.File

fun direct(name: String, value: Any?, unit: String? = null, source: String, kind: Classification = Classification.DIRECT_API, path: String? = null): TelemetryValue =
    if (value == null) missing(name, source, path) else TelemetryValue(name, value.toString(), value.toString(), unit, source, kind, path = path)

fun missing(name: String, source: String, path: String? = null, reason: String? = null) =
    TelemetryValue(name, source = source, classification = Classification.UNAVAILABLE, error = reason, path = path)

fun failure(name: String, source: String, error: Throwable, path: String? = null) =
    TelemetryValue(name, source = source, classification = Classification.ERROR, error = error.javaClass.simpleName + ": " + (error.message ?: "unknown"), path = path)

fun readKernel(path: String, name: String): TelemetryValue = try {
    if (!File(path).exists()) missing(name, path, path, "Missing file") else {
        val raw = File(path).readText().trim()
        if (raw.isBlank()) missing(name, path, path, "Empty file")
        else TelemetryValue(name, raw.take(4096), raw.take(4096), null, path, Classification.DIRECT_KERNEL, path = path)
    }
} catch (e: Exception) { failure(name, path, e, path) }
