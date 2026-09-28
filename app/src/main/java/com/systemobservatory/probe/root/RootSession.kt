package com.systemobservatory.probe.root

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** One interactive su process. No caller can submit a shell command string. */
class RootSession private constructor(private val process: Process) : Closeable {
    private val input = BufferedReader(InputStreamReader(process.inputStream))
    private val output = BufferedWriter(OutputStreamWriter(process.outputStream))
    private val reader = Executors.newSingleThreadExecutor { task -> Thread(task, "root-probe-reader").apply { isDaemon = true } }

    val active: Boolean get() = process.isAlive

    /** This is the only permitted identity command. */
    fun uid(): Int? = execute("id -u", 90).lines().mapNotNull { it.trim().toIntOrNull() }.firstOrNull()

    fun readFile(path: String): String = execute("cat '${safePath(path)}'", 10)

    fun listDirectory(path: String): List<String> = execute("ls -1 '${safePath(path)}'", 10)
        .lineSequence().map(String::trim).filter { it.matches(Regex("[A-Za-z0-9_.-]+")) }.take(512).toList()

    /** Optional provider hint; a failure must not affect a verified root grant. */
    fun suVersion(): String? = try { execute("su -v", 5).take(120).ifBlank { null } } catch (_: Exception) { null }

    @Synchronized
    private fun execute(command: String, timeoutSeconds: Long): String {
        check(process.isAlive) { "Root shell is no longer active" }
        val marker = "__PROBE_${UUID.randomUUID().toString().replace("-", "")}__"
        val future = reader.submit<Pair<String, Int>> {
            val lines = StringBuilder()
            while (true) {
                val line = input.readLine() ?: throw IllegalStateException("Root shell closed")
                if (line.startsWith("$marker:")) {
                    val exitCode = line.substringAfter(':').trim().toIntOrNull() ?: -1
                    return@submit lines.toString().trimEnd() to exitCode
                }
                if (lines.length < 131072) lines.append(line).append('\n')
            }
            @Suppress("UNREACHABLE_CODE")
            "" to -1
        }
        val result = try {
            output.write(command)
            output.write("\nprintf '\\n$marker:%s\\n' \"\$?\"\n")
            output.flush()
            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: Exception) {
            future.cancel(true)
            close()
            throw e
        }
        val (text, exitCode) = result
        if (exitCode != 0) throw IllegalStateException("Read-only root command failed ($exitCode): ${text.take(160)}")
        return text
    }

    override fun close() {
        reader.shutdownNow()
        process.destroy()
    }

    companion object {
        private val allowedRoots = listOf("/proc/", "/sys/")
        internal fun safePath(path: String): String {
            require(allowedRoots.any { path.startsWith(it) || path == it.dropLast(1) })
            require(path.matches(Regex("/[A-Za-z0-9_./-]+")) && path.split('/').none { it == ".." || it == "." })
            return path
        }

        fun open(): RootSession = RootSession(ProcessBuilder("su").redirectErrorStream(true).start())
    }
}
