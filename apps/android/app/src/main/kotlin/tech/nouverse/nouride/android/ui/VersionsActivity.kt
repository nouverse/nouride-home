package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import tech.nouverse.nouride.android.R

/**
 * "Versions and updates" on a phone, opened from More: its back and its name on top, then the two cards
 * (`VersionsPage`), which a wide screen shows as a section of its sidebar instead.
 */
class VersionsActivity : Activity() {
  private lateinit var ui: Ui
  private lateinit var page: VersionsPage

  /** In the appearance chosen on the More tab (`Appearances`). */
  override fun attachBaseContext(base: Context) = super.attachBaseContext(Appearances.wrap(base))

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    ui = Ui(this)
    ui.lightSystemBars(window)
    page = VersionsPage(this, ui, twoColumns = false)
    setContentView(views())
    FocusRing.follow(this)
    // Home's card for a new Nouride: the update starts here, where its progress is followed, once the check finds it.
    if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_UPDATE_NOURIDE, false)) page.updateNourideWhenFound()
  }

  override fun onResume() {
    super.onResume()
    page.resume()
  }

  override fun onPause() {
    page.pause()
    super.onPause()
  }

  override fun onDestroy() {
    page.close()
    super.onDestroy()
  }

  private fun views(): View {
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      background = ui.page()
    }
    root.addView(backBar(this, ui, R.string.versions_title))
    val column = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(24))
      addView(page.cards, ui.fullWidth())
    }
    root.addView(ScrollView(this).apply { addView(column) }, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
    ))
    return root
  }

  companion object {
    /** Opened from Home's card for a new Nouride: start its update. */
    const val EXTRA_UPDATE_NOURIDE = "update_nouride"
  }
}

/** The top of a screen opened from More on a phone: its back, which closes it, and its name. */
internal fun backBar(activity: Activity, ui: Ui, titleId: Int): View = LinearLayout(activity).apply {
  orientation = LinearLayout.HORIZONTAL
  gravity = Gravity.CENTER_VERTICAL
  setPadding(ui.dp(8), ui.dp(8), ui.dp(16), ui.dp(8))
  minimumHeight = ui.dp(56)
  addView(ImageView(context).apply {
    setImageResource(R.drawable.ic_back)
    contentDescription = activity.getString(R.string.close)
    setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
    setOnClickListener { activity.finish() }
  }, LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)))
  addView(ui.text(18f, R.color.ink, bold = true).apply {
    setText(titleId)
    setPadding(ui.dp(6), 0, 0, 0)
  })
}
