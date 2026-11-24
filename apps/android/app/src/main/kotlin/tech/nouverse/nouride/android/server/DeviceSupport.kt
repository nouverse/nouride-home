package tech.nouverse.nouride.android.server

import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Whether this device can run Nouride at all. proot and every program in Debian are aarch64, which needs a 64-bit
 * kernel. A 32-bit userspace on a 64-bit kernel runs them (most Google TV and Android TV boxes,
 * experiments/2026-11-24-android-tv/); a 32-bit kernel refuses to exec them. Neither Java's `os.arch` nor `uname` in the
 * app's process tells the two apart (a 32-bit app on a 64-bit kernel reads `armv8l`), so the app asks the kernel:
 * it runs proot's version check once and looks at how it failed, if it did.
 */
object DeviceSupport {
  @Volatile
  private var known: Boolean? = null

  /** The kernel's answer, asked once per process: it cannot change while the app runs. */
  fun runs(proot: String): Boolean = known ?: runsFrom(tryProot(proot)).also { known = it }

  /**
   * From how starting proot failed ([startFailure], null when it started): a device that cannot run Nouride is one
   * whose kernel refused the executable's format (ENOEXEC, "error=8, Exec format error" from `ProcessBuilder`). Any
   * other failure is not this one and is left to the start that follows, which reports it as it always has.
   */
  fun runsFrom(startFailure: String?): Boolean {
    if (startFailure == null) return true
    return !(startFailure.contains("error=8,") || startFailure.contains("Exec format error", ignoreCase = true))
  }

  /** Runs `proot --version`; null when it started, else why it did not. */
  private fun tryProot(proot: String): String? = try {
    val process = ProcessBuilder(proot, "--version").redirectErrorStream(true).start()
    process.outputStream.close()
    process.inputStream.use { it.readBytes() }
    if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroy()
    null
  } catch (e: IOException) {
    e.message ?: e.toString()
  }
}
