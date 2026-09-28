package com.systemobservatory.probe.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RootSessionTest {
    @Test fun onlyReadOnlyTelemetryPathsAreAccepted() {
        assertEquals("/proc/stat", RootSession.safePath("/proc/stat"))
        assertEquals("/sys/class/thermal/thermal_zone3/temp", RootSession.safePath("/sys/class/thermal/thermal_zone3/temp"))
        assertThrows(IllegalArgumentException::class.java) { RootSession.safePath("/proc/../data/data/secret") }
        assertThrows(IllegalArgumentException::class.java) { RootSession.safePath("/data/local/tmp/file") }
        assertThrows(IllegalArgumentException::class.java) { RootSession.safePath("/sys/class/thermal/zone;rm") }
    }
}
