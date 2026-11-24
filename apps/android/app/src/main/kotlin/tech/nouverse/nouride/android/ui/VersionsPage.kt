package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import tech.nouverse.nouride.android.Layout
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.server.Place
import tech.nouverse.nouride.android.server.ServerState
import tech.nouverse.nouride.android.server.Where
import tech.nouverse.nouride.android.update.Release
import tech.nouverse.nouride.android.update.Updates
import java.io.IOException
import kotlin.concurrent.thread

/**
 * "Versions and updates": which version of Nouride Home this is and which of Nouride runs, each with Check for updates
 * and, when there is a newer one, its update in one press. The app's update is the one Home offers (`AppUpdater`);
 * Nouride's is the one its dashboard offers (`NourideCard`). A screen of its own on a phone (`VersionsActivity`), a
 * section of the sidebar on a wide screen, where the two cards stand side by side from 960 dp ([twoColumns]).
 */
internal class VersionsPage(private val activity: Activity, private val ui: Ui, twoColumns: Boolean) {
  private val layout = Layout(activity)
  private val app = VersionCard(activity, ui, R.string.app_name, ::appPressed)
  private val nouride = NourideCard(activity, ui)
  private val updater = AppUpdater(activity, ::waits, onChange = ::showApp)

  /** Nouride's version and its update are the phone's own server's: no card while it lives on another machine. */
  private var remote = Place(activity).where() == Where.REMOTE

  /** What the last press of Check for updates found; the release itself is in `Updates`. */
  private var appStep = AppStep.UP_TO_DATE
  private val listener: () -> Unit = {
    nouride.serverChanged()
    showApp()
  }

  /** The two cards: one under the other, or side by side. */
  val cards: View = if (twoColumns) {
    val side = Columns.stack(activity).apply { addView(nouride.view, ui.fullWidth()) }
    Columns(activity, app.view, side, firstWeight = 0.5f, gapPx = ui.dp(20), aloneMaxPx = ui.dp(600))
  } else {
    LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      addView(app.view, ui.fullWidth())
      addView(nouride.view, ui.fullWidth(12))
    }
  }

  init {
    nouride.view.visibility = if (remote) View.GONE else View.VISIBLE
  }

  /** Home's card for a new Nouride: the update starts here, where its progress is followed, once the check finds it. */
  fun updateNourideWhenFound() {
    if (!remote) nouride.updateWhenFound()
  }

  /** When the page comes into view: where Nouride runs may have changed since (Settings, on a wide screen). */
  fun resume() {
    remote = Place(activity).where() == Where.REMOTE
    nouride.view.visibility = if (remote) View.GONE else View.VISIBLE
    ServerState.listen(listener)
    if (updater.resume()) Updates.waiting(activity)?.let(updater::start)
    showApp()
    if (!remote) nouride.resume()
  }

  fun pause() {
    ServerState.unlisten(listener)
  }

  /** On the screen's close: a download goes on in Nouride, and the card picks it up when the page opens again. */
  fun close() {
    nouride.close()
  }

  private fun waits() = Updates.waitsFor(ServerState.phase, startPressed = false) { layout.installed }

  private fun appPressed(button: VersionButton) {
    when (button) {
      VersionButton.CHECK -> checkApp()
      VersionButton.UPDATE -> Updates.waiting(activity)?.let(updater::start)
      VersionButton.ALLOW -> updater.allowing()
      VersionButton.INSTALL, VersionButton.NONE -> Unit
    }
  }

  /** The feed asked now, however fresh the last answer, as More's Check for updates did. */
  private fun checkApp() {
    if (appStep == AppStep.CHECKING || updater.busy) return
    appStep = AppStep.CHECKING
    showApp()
    thread(name = "update-check-now", isDaemon = true) {
      val failed = try {
        Updates.checkNow(activity)
        false
      } catch (e: IOException) {
        true
      }
      activity.runOnUiThread {
        if (activity.isDestroyed) return@runOnUiThread
        appStep = if (failed) AppStep.CHECK_FAILED else AppStep.UP_TO_DATE
        showApp()
      }
    }
  }

  /** The app's card, from what the updater is doing and what the last check of the feed found. */
  private fun showApp() {
    val waiting: Release? = Updates.waiting(activity)
    val step = when {
      updater.state == AppUpdater.State.DOWNLOADING -> AppStep.DOWNLOADING
      updater.state == AppUpdater.State.INSTALLING -> AppStep.INSTALLING
      appStep == AppStep.CHECKING -> AppStep.CHECKING
      // As on Home's card: without Android's permission neither Update nor Try again goes anywhere.
      waiting != null && !Updates.canInstall(activity) -> AppStep.ALLOW
      updater.state == AppUpdater.State.FAILED && waiting != null -> AppStep.DOWNLOAD_FAILED
      Updates.failed(activity) && waiting != null -> AppStep.INSTALL_FAILED
      // While Nouride is first set up, Update waits for it, as on Home (`AppUpdater.start`).
      waiting != null -> AppStep.READY
      appStep == AppStep.CHECK_FAILED -> AppStep.CHECK_FAILED
      else -> AppStep.UP_TO_DATE
    }
    val look = VersionLooks.app(step)
    app.render(look, Updates.installedVersion(activity), waiting?.version, updater.percent)
  }
}
