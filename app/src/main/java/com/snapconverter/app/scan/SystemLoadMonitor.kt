package com.snapconverter.app.scan

import android.os.Process
import android.os.SystemClock
import java.io.File

/**
 * One sample of how busy the machine is during a batch.
 *
 * Percentages are shares of the whole chip, not of one core: 100% app CPU means
 * every core is saturated by this app. A null means the number could not be
 * read on this device — the UI says so rather than showing a zero that looks
 * like an idle GPU.
 */
data class SystemLoad(
    val appCpuPercent: Float = 0f,
    val systemCpuPercent: Float? = null,
    val gpuPercent: Float? = null,
) {
    val gpuReadable: Boolean get() = gpuPercent != null
}

/**
 * Samples CPU and GPU utilisation while the batch runs.
 *
 * App CPU comes from [Process.getElapsedCpuTime], a public API that needs no
 * permission and works in the sandbox. System CPU and GPU busy come from sysfs
 * (`/proc/stat`, Adreno's KGSL nodes), which SELinux may refuse to a
 * third-party app; both are therefore optional and probed once. Nothing here
 * ever throws: an unreadable counter is reported as unknown.
 */
class SystemLoadMonitor {

    private val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    private var lastWallMs = 0L
    private var lastAppCpuMs = 0L
    private var lastSystemBusy = 0L
    private var lastSystemTotal = 0L

    private var gpuSource: GpuSource? = null
    private var gpuProbed = false

    /** Call once before the first [sample] so the first delta is meaningful. */
    fun reset() {
        lastWallMs = SystemClock.elapsedRealtime()
        lastAppCpuMs = Process.getElapsedCpuTime()
        lastSystemBusy = 0L
        lastSystemTotal = 0L
        readSystemCpu()
        if (!gpuProbed) {
            gpuSource = probeGpu()
            gpuProbed = true
        }
        gpuSource?.let { readGpu(it) }
    }

    fun sample(): SystemLoad {
        val wall = SystemClock.elapsedRealtime()
        val cpu = Process.getElapsedCpuTime()
        val wallDelta = (wall - lastWallMs).coerceAtLeast(1L)
        val cpuDelta = (cpu - lastAppCpuMs).coerceAtLeast(0L)
        lastWallMs = wall
        lastAppCpuMs = cpu
        val appPercent = (cpuDelta.toFloat() / (wallDelta * cores) * 100f).coerceIn(0f, 100f)
        return SystemLoad(
            appCpuPercent = appPercent,
            systemCpuPercent = readSystemCpu(),
            gpuPercent = gpuSource?.let { readGpu(it) },
        )
    }

    /* ------------------------------------------------------------- system cpu */

    private fun readSystemCpu(): Float? {
        val line = runCatching {
            File("/proc/stat").bufferedReader().use { it.readLine() }
        }.getOrNull() ?: return null
        if (!line.startsWith("cpu ")) return null
        val fields = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (fields.size < 5) return null
        val idle = fields[3] + (fields.getOrNull(4) ?: 0L)
        val total = fields.sum()
        val busy = total - idle
        val prevTotal = lastSystemTotal
        val prevBusy = lastSystemBusy
        lastSystemTotal = total
        lastSystemBusy = busy
        if (prevTotal == 0L) return null
        val totalDelta = total - prevTotal
        if (totalDelta <= 0L) return null
        return ((busy - prevBusy).toFloat() / totalDelta * 100f).coerceIn(0f, 100f)
    }

    /* -------------------------------------------------------------------- gpu */

    private enum class GpuKind { PERCENT, LOAD, BUSY_PAIR }

    private data class GpuSource(val file: File, val kind: GpuKind)

    private fun probeGpu(): GpuSource? {
        val candidates = listOf(
            GpuSource(File("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"), GpuKind.PERCENT),
            GpuSource(File("/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load"), GpuKind.LOAD),
            GpuSource(File("/sys/class/kgsl/kgsl-3d0/gpubusy"), GpuKind.BUSY_PAIR),
        )
        return candidates.firstOrNull { source ->
            runCatching { source.file.readText().isNotBlank() }.getOrDefault(false)
        }
    }

    private fun readGpu(source: GpuSource): Float? {
        val text = runCatching { source.file.readText().trim() }.getOrNull() ?: return null
        return when (source.kind) {
            // "42 %"
            GpuKind.PERCENT -> text.filter { it.isDigit() }.toFloatOrNull()?.coerceIn(0f, 100f)
            GpuKind.LOAD -> text.toFloatOrNull()?.coerceIn(0f, 100f)
            // "<busy> <total>" in GPU ticks *since the previous read*, so the
            // ratio is already the interval's utilisation — no delta needed.
            // A zero total is an idle interval, not an unreadable counter: on a
            // Snapdragon this is the one node a third-party app may open, and
            // reporting it as unknown would claim the device hides a number it
            // just handed over.
            GpuKind.BUSY_PAIR -> {
                val parts = text.split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
                if (parts.size < 2) return null
                if (parts[1] <= 0L) return 0f
                (parts[0].toFloat() / parts[1] * 100f).coerceIn(0f, 100f)
            }
        }
    }
}
