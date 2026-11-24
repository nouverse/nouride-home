package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast
import tech.nouverse.nouride.android.Layout
import tech.nouverse.nouride.android.Nouride
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.server.NetworkAccess
import tech.nouverse.nouride.android.server.RemoteHost
import tech.nouverse.nouride.android.server.ServerService
import tech.nouverse.nouride.android.server.Where

/**
 * The Connect tab: Nouride on a tablet, a computer or another phone on the same Wi-Fi. A QR code its camera opens, the
 * address large to type or share, and how to sign in there. Before Nouride is running, or off Wi-Fi, the cat says so
 * instead: an address that leads nowhere is not shown. A switch on top keeps Nouride to this phone (`NetworkAccess`),
 * for a Wi-Fi the person does not trust; it is on, as installed.
 *
 * On a wide screen ([shape]) it is the sidebar's Connect, and from 960 dp the code stands on the left with the switch
 * over the address beside it, as the desktop's Connect (`spec/layout.md`).
 */
internal class ConnectTab(private val activity: Activity, private val ui: Ui, private val shape: Shape, private val onGoHome: () -> Unit) {
  private val column = ui.column(shape)
  private val twoColumns = shape == Shape.TWO_COLUMNS
  private val layout = Layout(activity)
  private var shown: Triple<Reach, String?, Boolean?>? = null
  private var shownRemote: String? = null

  val view: ScrollView = ui.scroll(column, shape)

  /** Rebuilt only when what it offers changed: the Wi-Fi's address can change while the tab is open. */
  fun render(look: Look, where: Where?, address: String?) {
    if (where == Where.REMOTE) {
      shown = null
      renderRemote(address.orEmpty())
      return
    }
    shownRemote = null
    val lan = SupportText.lanAddress()
    // Null before Nouride is installed: there is no `.env` to change yet, and no switch.
    val open = if (layout.installEnv.isFile) NetworkAccess.open(layout.installEnv.readText()) else null
    val reach = Tabs.reach(look, lan, openToOthers = open != false)
    val address = if (reach == Reach.READY) "http://$lan:${Nouride.PORT}" else null
    if (shown == Triple(reach, address, open)) return
    shown = Triple(reach, address, open)
    column.removeAllViews()
    column.addView(ui.top(shape, R.string.tab_connect))
    val access = open?.let(::access)
    if (reach == Reach.READY) return ready(address!!, access = access)
    val card = when (reach) {
      Reach.OFF -> notYet(R.drawable.mascot_hello, R.string.connect_off_title, R.string.connect_off_body, home = true)
      Reach.THIS_PHONE -> notYet(R.drawable.mascot_hello, R.string.this_phone_title, R.string.this_phone_body, home = false)
      else -> notYet(R.drawable.mascot_unplugged, R.string.no_wifi_title, R.string.no_wifi, home = false)
    }
    if (twoColumns) {
      // The switch beside the cat; before Nouride is installed there is none, and the cat stands alone.
      val side = Columns.stack(activity).apply { if (access != null) addView(access, ui.fullWidth()) }
      column.addView(columns(card, side), ui.fullWidth(16))
      return
    }
    if (access != null) column.addView(access, ui.fullWidth(16))
    column.addView(card, ui.fullWidth(16))
  }

  private fun columns(main: View, side: LinearLayout, firstWeight: Float = 0.5f) =
    Columns(activity, main, side, firstWeight, gapPx = ui.dp(20), aloneMaxPx = ui.dp(600))

  /**
   * Nouride on another computer: the same code and address as this phone's own, for that computer's address. No
   * switch, and no Wi-Fi needed: this phone runs nothing to open, and the address works from anywhere.
   */
  private fun renderRemote(address: String) {
    if (shownRemote == address) return
    shownRemote = address
    column.removeAllViews()
    column.addView(ui.top(shape, R.string.tab_connect))
    ready(address, RemoteHost.shown(address), R.string.remote_connect_title, R.string.remote_connect_body, R.string.remote_connect_sign_in)
  }

  /** Open from other devices: the `.env`'s `HOST`, and Nouride started again for a moment to read it. */
  private fun access(open: Boolean): View = ui.card().apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    addView(LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      addView(ui.text(17f, R.color.ink, bold = true).apply { setText(R.string.access_title) })
      addView(ui.text(14f, R.color.muted).apply { setText(R.string.access_body) }, ui.fullWidth(4))
    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    addView(Switch(activity).apply {
      isChecked = open
      contentDescription = activity.getString(R.string.access_title)
      val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
      // Off must read as off in both themes: a grey thumb on the line colour, not the card's own colour.
      thumbTintList = ColorStateList(states, intArrayOf(ui.color(R.color.brand_text), ui.color(R.color.muted)))
      trackTintList = ColorStateList(states, intArrayOf(ui.color(R.color.brand_text), ui.color(R.color.line)))
      setOnCheckedChangeListener { _, on ->
        ServerService.setOpenToOthers(activity, on)
        Toast.makeText(activity, R.string.access_restarting, Toast.LENGTH_SHORT).show()
      }
    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
      leftMargin = ui.dp(12)
    })
  }

  private fun notYet(image: Int, heading: Int, sentence: Int, home: Boolean): View =
    ui.card(padding = 24).apply {
      gravity = Gravity.CENTER_HORIZONTAL
      addView(ImageView(activity).apply {
        setImageResource(image)
        adjustViewBounds = true
      }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ui.dp(160)))
      addView(ui.text(20f, R.color.ink, bold = true, center = true).apply { setText(heading) }, ui.fullWidth(16))
      addView(ui.text(15f, R.color.muted, center = true).apply { setText(sentence) }, ui.fullWidth(6))
      if (home) addView(ui.primary { onGoHome() }.apply { setText(R.string.go_home) }, ui.fullWidth(20))
    }

  /**
   * The code, the address as a person reads it ([shown]), Copy and Share, and how to sign in there, under the switch
   * ([access]) on a phone. In two columns the code stands alone on the left and the rest beside it.
   */
  private fun ready(
    address: String,
    shown: String = address,
    heading: Int = R.string.connect_ready_title,
    sentence: Int = R.string.connect_ready_body,
    signIn: Int = R.string.connect_sign_in_body,
    access: View? = null,
  ) {
    val words = ui.card(padding = 24).apply { gravity = Gravity.CENTER_HORIZONTAL }
    words.addView(ui.text(20f, R.color.ink, bold = true, center = true).apply { setText(heading) })
    words.addView(ui.text(15f, R.color.muted, center = true).apply { setText(sentence) }, ui.fullWidth(6))
    val code = qrView(address, if (twoColumns) 208 else 232)
    val pill = addressPill(shown)
    val buttons = shareButtons(address)
    val signInCard = ui.card().apply {
      addView(ui.text(17f, R.color.ink, bold = true).apply { setText(R.string.connect_sign_in_title) })
      addView(ui.text(15f, R.color.muted).apply { setText(signIn) }, ui.fullWidth(6))
    }
    if (twoColumns) {
      val codeCard = ui.card(padding = 16).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        addView(code, LinearLayout.LayoutParams(ui.dp(208), ui.dp(208)))
      }
      words.addView(pill, ui.fullWidth(18))
      words.addView(buttons, ui.fullWidth(16))
      val side = Columns.stack(activity).apply {
        if (access != null) addView(access, ui.fullWidth().apply { bottomMargin = ui.dp(16) })
        addView(words, ui.fullWidth())
        addView(signInCard, ui.fullWidth(12))
      }
      column.addView(columns(codeCard, side, firstWeight = 0.4f), ui.fullWidth(16))
      return
    }
    if (access != null) column.addView(access, ui.fullWidth(16))
    words.addView(code, LinearLayout.LayoutParams(ui.dp(232), ui.dp(232)).apply { topMargin = ui.dp(18) })
    words.addView(pill, ui.fullWidth(18))
    words.addView(buttons, ui.fullWidth(16))
    column.addView(words, ui.fullWidth(16))
    column.addView(signInCard, ui.fullWidth(12))
  }

  /** Dark on white in the dark theme too: a camera reads a code by its contrast. */
  private fun qrView(address: String, sizeDp: Int): View = ImageView(activity).apply {
    setImageDrawable(BitmapDrawable(activity.resources, qr(address)).apply { isFilterBitmap = false })
    background = ui.rounded(Color.WHITE, 20)
    val inset = ui.dp(12 * sizeDp / 232)
    setPadding(inset, inset, inset, inset)
    contentDescription = activity.getString(R.string.connect_qr_description)
  }

  /** An address is an identifier: DM Mono, as the dashboard sets one. */
  private fun addressPill(shown: String): View = ui.text(22f, R.color.brand_text, bold = true, center = true).apply {
    typeface = Fonts.typeface(context, Type.MONO)
    text = shown
    setTextIsSelectable(true)
    // One line for the phone's own address; a tunnel's long name takes a second line rather than lose its end.
    maxLines = 2
    if (Build.VERSION.SDK_INT >= 26) setAutoSizeTextTypeUniformWithConfiguration(14, 22, 1, TypedValue.COMPLEX_UNIT_SP)
    background = ui.rounded(ui.color(R.color.brand_soft), 20)
    setPadding(ui.dp(12), ui.dp(14), ui.dp(12), ui.dp(14))
  }

  private fun shareButtons(address: String): View = LinearLayout(activity).apply {
    orientation = LinearLayout.HORIZONTAL
    addView(ui.secondary { copy(address) }.apply { setText(R.string.copy) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    addView(View(activity), LinearLayout.LayoutParams(ui.dp(12), 1))
    addView(ui.primary { share(address) }.apply { setText(R.string.share_address) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
  }

  /** The code with its four-module quiet zone, a few pixels a module: the view scales it without smoothing. */
  private fun qr(text: String): Bitmap {
    val modules = QrCode.encode(text)
    val quiet = 4
    val scale = 8
    val side = (modules.size + 2 * quiet) * scale
    val pixels = IntArray(side * side) { Color.WHITE }
    for (row in modules.indices) for (column in modules.indices) {
      if (!modules[row][column]) continue
      for (dy in 0 until scale) for (dx in 0 until scale) {
        pixels[((row + quiet) * scale + dy) * side + (column + quiet) * scale + dx] = Color.BLACK
      }
    }
    return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
  }

  private fun copy(address: String) {
    activity.getSystemService(ClipboardManager::class.java)
      .setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.app_name), address))
    Toast.makeText(activity, R.string.copied, Toast.LENGTH_SHORT).show()
  }

  private fun share(address: String) {
    activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, address), null))
  }
}
