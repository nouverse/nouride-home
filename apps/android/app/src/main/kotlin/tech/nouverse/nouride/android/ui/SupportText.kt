package tech.nouverse.nouride.android.ui

import java.io.File
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * What "Technical Logs" says, for whoever helps the person: the technical words the main screen never shows. The
 * summary first, then the app's own events, then Nouride's log (the installer's until Nouride is installed), newest
 * last, so the screen opens on what just happened. Pure apart from reading the files, so the shape is tested.
 */
object SupportText {
  /** One fact a line, `name: value`, in a fixed order. */
  fun summary(facts: List<Pair<String, String>>): String = facts.joinToString("\n") { (name, value) -> "$name: $value" }

  /** The last [lines] lines of [text], without the empty line a log's last newline leaves. */
  fun tail(text: String, lines: Int): String = text.trimEnd('\n').lines().takeLast(lines).joinToString("\n")

  /** The report as it is copied or shared: each part under its own heading. */
  fun report(summary: String, logs: List<Pair<String, String>>): String = buildString {
    append(summary)
    for ((name, text) in logs) {
      append("\n\n== ").append(name).append(" ==\n")
      append(text.ifBlank { "(empty)" })
    }
  }

  /** The end of a log file, at most [bytes] of it; empty when there is none yet. */
  fun readTail(file: File, lines: Int, bytes: Long = 64 * 1024): String {
    if (!file.isFile) return ""
    RandomAccessFile(file, "r").use { raf ->
      val start = maxOf(0L, raf.length() - bytes)
      raf.seek(start)
      val buffer = ByteArray((raf.length() - start).toInt())
      raf.readFully(buffer)
      // A cut in the middle of a line leaves half a line first: drop it.
      val text = String(buffer).let { if (start > 0) it.substringAfter('\n') else it }
      return tail(text, lines)
    }
  }

  /** The device's address on the home network, or null without one ([lanAddressOf]). */
  fun lanAddress(): String? = lanAddressOf(
    NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
      .filter { it.isUp && !it.isLoopback }
      .flatMap { nic -> nic.inetAddresses.toList().filterIsInstance<Inet4Address>().map { nic.name to it.hostAddress!! } },
  )

  /**
   * The first IPv4 address on Wi-Fi (`wlan*`), else on a network cable (`eth*`, as many TV boxes are wired); never the
   * mobile network's, which nothing at home can reach. Pure: [addresses] are (interface, address) pairs.
   */
  fun lanAddressOf(addresses: List<Pair<String, String>>): String? =
    addresses.firstOrNull { it.first.startsWith("wlan") }?.second
      ?: addresses.firstOrNull { it.first.startsWith("eth") }?.second
}
