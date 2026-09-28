package com.systemobservatory.probe.telemetry

object Parsers {
    fun thermalCelsius(raw: String): Double? {
        val value = raw.trim().toDoubleOrNull() ?: return null
        val celsius = if (kotlin.math.abs(value) >= 1000) value / 1000.0 else value
        return celsius.takeIf { it in -80.0..200.0 }
    }

    fun cpuTotals(line: String): Pair<Long, Long>? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.firstOrNull() != "cpu" || parts.size < 5) return null
        val values = parts.drop(1).map { it.toLongOrNull() ?: return null }
        val idle = values[3] + values.getOrElse(4) { 0L }
        return values.sum() to idle
    }

    fun cpuUsage(first: Pair<Long, Long>, second: Pair<Long, Long>): Double? {
        val total = second.first - first.first
        val idle = second.second - first.second
        return if (total > 0 && idle >= 0 && idle <= total) 100.0 * (total - idle) / total else null
    }
}
