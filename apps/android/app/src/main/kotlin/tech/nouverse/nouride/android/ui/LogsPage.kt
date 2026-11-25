package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import tech.nouverse.nouride.android.Layout
import tech.nouverse.nouride.android.Nouride
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.server.NetworkAccess
import tech.nouverse.nouride.android.server.ServerState
import tech.nouverse.nouride.android.update.Updates

/**
 * "Technical Logs": everything technical, for whoever helps the person. The log scrolls on its own, given the rest of
 * the screen rather than a box inside a scrolling page, and opens at its end. Copy and Share send the whole report,
 * Share straight into a chat. A screen of its own on a phone (`SupportActivity`), the last section of the sidebar on a
 * wide screen, where it takes the whole page. A wide screen is often short (a TV, a phone turned sideways), so there
 * ([wide]) the summary scrolls in the log's own box, above the log, rather than leave the log no room.
 */
internal class LogsPage(private val activity: Activity, private val ui: Ui, wide: Boolean = false) {
  private val layout = Layout(activity)
  private val summary: TextView = mono(R.color.ink)
  private val log: TextView = mono(R.color.muted).apply {
    // Long log lines scroll sideways rather than wrapping into a wall of half-lines.
    setHorizontallyScrolling(true)
  }
  private val scroll = ScrollView(activity).apply {
    isFillViewport = true
    val lines = HorizontalScrollView(context).apply { addView(log) }
    if (!wide) return@apply addView(lines)
    addView(LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      addView(summary)
      addView(View(context).apply { setBackgroundColor(ui.color(R.color.line)) }, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(1),
      ).apply { setMargins(0, ui.dp(12), 0, ui.dp(12)) })
      addView(lines)
    })
  }
  private var report = ""

  val view: LinearLayout = LinearLayout(activity).apply {
    orientation = LinearLayout.VERTICAL
    if (!wide) {
      addView(ui.card(padding = 14).apply { addView(summary) }, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
      ).apply { setMargins(ui.dp(16), 0, ui.dp(16), ui.dp(12)) })
    }
    addView(ui.card(padding = 14).apply { addView(scroll) }, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
    ).apply { setMargins(ui.dp(16), 0, ui.dp(16), 0) })
    addView(LinearLayout(activity).apply {
      orientation = LinearLayout.HORIZONTAL
      setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(16))
      addView(ui.secondary { copy() }.apply { setText(R.string.copy) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
      addView(View(context), LinearLayout.LayoutParams(ui.dp(12), 1))
      addView(ui.primary { share() }.apply { setText(R.string.share) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    })
  }

  private fun mono(colorId: Int): TextView = ui.text(12f, colorId).apply {
    typeface = Fonts.typeface(context, Type.MONO)
    setTextIsSelectable(true)
  }

  /** Read again each time the page comes into view. */
  fun load() {
    val health = ServerState.health
    val lan = SupportText.lanAddress()
    val facts = SupportText.summary(listOf(
      "state" to "${ServerState.phase.name.lowercase()} ${ServerState.detail}".trim(),
      "health" to (health?.summary() ?: "not asked yet"),
      "local" to Nouride.DASHBOARD_URL,
      "lan" to when {
        layout.installEnv.isFile && !NetworkAccess.open(layout.installEnv.readText()) -> "off (this phone only)"
        lan == null -> "no Wi-Fi"
        else -> "http://$lan:${Nouride.PORT}/dashboard/"
      },
      "app" to Updates.installedVersion(activity),
      "android" to "${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL})",
    ))
    val nouride = if (layout.installed) layout.daemonLog else layout.installLog
    val logs = listOf(
      layout.serviceLog.name to SupportText.readTail(layout.serviceLog, 40),
      nouride.name to SupportText.readTail(nouride, 300),
    )
    report = SupportText.report(facts, logs)
    summary.text = facts
    log.text = SupportText.report("", logs).trimStart()
    // Scrolled, not `fullScroll`: that also moves focus to the log, and a remote on the sidebar would lose its place.
    scroll.post { scroll.scrollTo(0, scroll.getChildAt(0).height) }
  }

  private fun copy() {
    activity.getSystemService(ClipboardManager::class.java)
      .setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.support_title), report))
    Toast.makeText(activity, R.string.copied, Toast.LENGTH_SHORT).show()
  }

  private fun share() {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
      .putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.support_title))
      .putExtra(Intent.EXTRA_TEXT, report)
    activity.startActivity(Intent.createChooser(send, null))
  }
}
