package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.webkit.WebView
import android.widget.ScrollView
import tech.nouverse.nouride.android.R

/**
 * A ring around whatever a remote's D-pad has reached, on every screen: the app's buttons draw no focused state of
 * their own (a pill, a soft card), so on a TV nobody could see where they are. Lavender, the colour of where the person
 * is, inside the view's own corners. Only out of touch mode: a finger never sees it, so phones look as they did.
 *
 * One listener on the window rather than a ring on each button, so a button made later (cards are rebuilt as states
 * change) is covered too. `FocusRingTest` checks every screen calls [follow].
 */
object FocusRing {
  fun follow(activity: Activity) {
    val ui = Ui(activity)
    var ringed: View? = null
    var before: Drawable? = null
    activity.window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { _, now ->
      ringed?.foreground = before
      ringed = null
      if (now == null || now.isInTouchMode || !rings(now)) return@addOnGlobalFocusChangeListener
      before = now.foreground
      now.foreground = ring(ui, now)
      ringed = now
    }
  }

  /** A page that scrolls, or the dashboard, is not a thing to press: it draws its own focus, or none is needed. */
  private fun rings(view: View): Boolean = view !is ScrollView && view !is WebView

  private fun ring(ui: Ui, view: View): Drawable = GradientDrawable().apply {
    setColor(Color.TRANSPARENT)
    setStroke(ui.dp(3), ui.color(R.color.place))
    cornerRadius = (view.background as? GradientDrawable)?.cornerRadius?.takeIf { it > 0f } ?: ui.dp(16).toFloat()
  }
}
