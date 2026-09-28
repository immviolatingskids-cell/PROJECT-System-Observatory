package com.systemobservatory.probe.root

import com.systemobservatory.probe.model.RootInfo
import com.systemobservatory.probe.model.RootState
import java.io.Closeable
import java.io.File

class RootManager : Closeable {
    var session: RootSession? = null
        private set

    var info: RootInfo = RootInfo(suAvailable = suBinaryFound(), state = if (suBinaryFound()) RootState.SU_AVAILABLE else RootState.NOT_CHECKED)
        private set

    fun request(): RootInfo {
        session?.close()
        session = null
        info = info.copy(requestAttempted = true, state = RootState.REQUESTING, granted = false, uid = null, sessionActive = false, error = null)
        return try {
            val candidate = RootSession.open()
            val uid = try { candidate.uid() } catch (e: Exception) { candidate.close(); throw e }
            if (uid != 0) {
                candidate.close()
                info = info.copy(suAvailable = true, state = RootState.DENIED, uid = uid, error = "su shell returned UID ${uid ?: "unknown"}")
            } else {
                session = candidate
                val version = candidate.suVersion()
                val provider = when {
                    version?.contains("KernelSU", true) == true -> "KernelSU"
                    version?.contains("Magisk", true) == true -> "Magisk"
                    version?.contains("APatch", true) == true -> "APatch"
                    else -> "Unknown"
                }
                info = info.copy(suAvailable = true, state = RootState.GRANTED, granted = true, uid = 0,
                    provider = provider, version = version, sessionActive = candidate.active)
            }
            info
        } catch (e: Exception) {
            info = info.copy(state = if (e is java.io.IOException || e is java.util.concurrent.TimeoutException) RootState.ERROR else RootState.DENIED,
                error = "${e.javaClass.simpleName}: ${e.message ?: "root request failed"}", sessionActive = false)
            info
        }
    }

    fun current(): RootInfo {
        val active = session?.active == true
        return if (info.granted && !active) info.copy(state = RootState.ERROR, granted = false, sessionActive = false, error = "Root session ended")
        else info.copy(sessionActive = active)
    }

    override fun close() { session?.close(); session = null }

    private fun suBinaryFound(): Boolean {
        val paths = System.getenv("PATH")?.split(':').orEmpty() + listOf("/system/bin", "/system/xbin", "/sbin")
        return paths.any { File(it, "su").exists() }
    }
}
