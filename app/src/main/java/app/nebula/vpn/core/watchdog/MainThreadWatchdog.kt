package app.nebula.vpn.core.watchdog

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import app.nebula.vpn.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Debug-only main-thread watchdog for detecting UI hangs and potential ANRs.
 * If the Android main looper is blocked for more than [timeoutMs] (default 1500ms),
 * it dumps all thread stack traces to disk and preserves them for diagnostics.
 */
object MainThreadWatchdog {

    private const val TAG = "MainThreadWatchdog"
    private const val DEFAULT_TIMEOUT_MS = 1500L
    private const val CHECK_INTERVAL_MS = 300L
    private const val MAX_SAVED_DUMPS = 10

    private val isRunning = AtomicBoolean(false)
    private val lastTick = AtomicLong(0L)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var watchdogThread: Thread? = null
    private var stabilityDir: File? = null

    data class WatchdogDump(
        val timestamp: Long,
        val durationBlockedMs: Long,
        val mainThreadStack: String,
        val fullDump: String,
        val file: File?
    )

    private val recentDumps = ConcurrentLinkedDeque<WatchdogDump>()

    fun getRecentDumps(): List<WatchdogDump> = recentDumps.toList()

    fun start(context: Context, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        if (!BuildConfig.DEBUG && !isWatchdogEnabledExplicitly(context)) {
            Log.d(TAG, "Watchdog disabled in non-debug build")
            return
        }

        if (isRunning.compareAndSet(false, true)) {
            val dir = File(context.filesDir, "stability")
            if (!dir.exists()) dir.mkdirs()
            stabilityDir = dir

            lastTick.set(SystemClock.uptimeMillis())

            watchdogThread = Thread({
                var lastDumpTime = 0L

                while (isRunning.get()) {
                    // Post canary to main looper
                    mainHandler.post {
                        lastTick.set(SystemClock.uptimeMillis())
                    }

                    java.util.concurrent.locks.LockSupport.parkNanos(CHECK_INTERVAL_MS * 1_000_000L)
                    if (Thread.interrupted()) {
                        break
                    }

                    val now = SystemClock.uptimeMillis()
                    val blockedTime = now - lastTick.get()

                    if (blockedTime >= timeoutMs) {
                        // Cooldown of 5s to prevent disk flood during persistent hang
                        if (now - lastDumpTime >= 5000L) {
                            lastDumpTime = now
                            captureThreadDump(blockedTime)
                        }
                    }
                }
            }, "Nebula-MainWatchdog").apply {
                isDaemon = true
                priority = Thread.MAX_PRIORITY
                start()
            }

            Log.i(TAG, "MainThreadWatchdog started (timeout: ${timeoutMs}ms, checkInterval: ${CHECK_INTERVAL_MS}ms)")
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            watchdogThread?.interrupt()
            watchdogThread = null
            Log.i(TAG, "MainThreadWatchdog stopped")
        }
    }

    private fun captureThreadDump(blockedMs: Long) {
        val now = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss.SSS", Locale.US).format(Date(now))
        val sb = StringBuilder()

        sb.append("=== NEBULA MAIN THREAD STALL DETECTED ===\n")
        sb.append("Timestamp: $dateStr\n")
        sb.append("Main thread blocked duration: ${blockedMs}ms (threshold: $DEFAULT_TIMEOUT_MS ms)\n\n")

        val allTraces = Thread.getAllStackTraces()
        val mainThread = Looper.getMainLooper().thread
        val mainTrace = allTraces[mainThread]

        sb.append("--- MAIN THREAD [id=${mainThread.id}, state=${mainThread.state}] ---\n")
        val mainStackStr = if (mainTrace != null && mainTrace.isNotEmpty()) {
            val stack = mainTrace.joinToString("\n") { "    at $it" }
            sb.append(stack).append("\n\n")
            stack
        } else {
            sb.append("    (No stack trace available or thread dead)\n\n")
            "No stack trace"
        }

        sb.append("--- OTHER ACTIVE THREADS (${allTraces.size - 1}) ---\n")
        for ((thread, stack) in allTraces) {
            if (thread == mainThread) continue
            sb.append("Thread: ${thread.name} [id=${thread.id}, priority=${thread.priority}, state=${thread.state}, daemon=${thread.isDaemon}]\n")
            if (stack.isNotEmpty()) {
                sb.append(stack.joinToString("\n") { "    at $it" }).append("\n")
            } else {
                sb.append("    (empty stack)\n")
            }
            sb.append("\n")
        }

        val fullDumpStr = sb.toString()
        Log.w(TAG, "MAIN THREAD BLOCKED FOR ${blockedMs}ms!\n$mainStackStr")

        // Save to stability folder
        var dumpFile: File? = null
        try {
            val dir = stabilityDir
            if (dir != null && dir.exists()) {
                val file = File(dir, "anr_stall_${now}.txt")
                file.writeText(fullDumpStr)
                dumpFile = file
                pruneOldDumps(dir)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist stall dump to disk", e)
        }

        val dumpObj = WatchdogDump(
            timestamp = now,
            durationBlockedMs = blockedMs,
            mainThreadStack = mainStackStr,
            fullDump = fullDumpStr,
            file = dumpFile
        )

        recentDumps.addLast(dumpObj)
        while (recentDumps.size > MAX_SAVED_DUMPS) {
            recentDumps.pollFirst()
        }
    }

    private fun pruneOldDumps(dir: File) {
        val files = dir.listFiles { _, name -> name.startsWith("anr_stall_") && name.endsWith(".txt") } ?: return
        if (files.size > MAX_SAVED_DUMPS) {
            files.sortedBy { it.lastModified() }
                .take(files.size - MAX_SAVED_DUMPS)
                .forEach { it.delete() }
        }
    }

    private fun isWatchdogEnabledExplicitly(context: Context): Boolean {
        return try {
            val sp = context.getSharedPreferences("nebula_perf_flags", Context.MODE_PRIVATE)
            sp.getBoolean("enable_watchdog", false)
        } catch (_: Exception) {
            false
        }
    }
}
