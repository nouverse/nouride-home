package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.LinearLayout
import tech.nouverse.nouride.android.R

/**
 * "Technical Logs" on a phone, opened from the end of More: its back and its name on top, then the page (`LogsPage`),
 * which a wide screen shows as the last section of its sidebar instead. The main screen only has a quiet link to it.
 */
class SupportActivity : Activity() {
  private lateinit var page: LogsPage

  /** In the appearance chosen on the More tab (`Appearances`). */
  override fun attachBaseContext(base: Context) = super.attachBaseContext(Appearances.wrap(base))

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val ui = Ui(this)
    ui.lightSystemBars(window)
    page = LogsPage(this, ui)
    setContentView(LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      background = ui.page()
      addView(backBar(this@SupportActivity, ui, R.string.support_title))
      addView(page.view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    })
    FocusRing.follow(this)
  }

  override fun onResume() {
    super.onResume()
    page.load()
  }
}
