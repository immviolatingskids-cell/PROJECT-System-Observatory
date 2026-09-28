package com.systemobservatory.probe.telemetry

import org.junit.Assert.*
import org.junit.Test

class ParsersTest {
    @Test fun thermalNormalization() {
        assertEquals(34.1, Parsers.thermalCelsius("34100")!!, 0.001)
        assertEquals(42.0, Parsers.thermalCelsius("42")!!, 0.001)
        assertNull(Parsers.thermalCelsius("garbage"))
        assertNull(Parsers.thermalCelsius("999999"))
    }
    @Test fun cpuUsageFromTwoSnapshots() {
        val first = Parsers.cpuTotals("cpu  100 0 100 800 0 0 0")!!
        val second = Parsers.cpuTotals("cpu  150 0 150 900 0 0 0")!!
        assertEquals(50.0, Parsers.cpuUsage(first, second)!!, 0.001)
        assertNull(Parsers.cpuTotals("bad"))
    }
}
