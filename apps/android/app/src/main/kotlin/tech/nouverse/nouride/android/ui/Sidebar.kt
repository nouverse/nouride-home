package tech.nouverse.nouride.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.FocusFinder
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import tech.nouverse.nouride.android.R

/**
 * The sections down the left on a wide screen, as the desktop's window has them (`spec/layout.md`): the cat on its
 * disc and the app's name on top, then Home, Connect, Versions and updates and Settings, and Technical Logs set apart
 * at the bottom. The open one sits in the soft lavender pill; a dot says something there needs the person. Each is
 * reached with a remote's D-pad and ringed (`FocusRing`); [SidebarLayout] keeps Left and Right between it and the page.
 */
internal class Sidebar(context: Context, private val ui: Ui, onSelect: (Section) -> Unit) {
  private class Item(val root: LinearLayout, val icon: ImageView, val label: TextView, val dot: View)

  private val items = LinkedHashMap<Section, Item>()
  private var open = Section.HOME

  private val column: LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(ui.dp(10), ui.dp(14), ui.dp(10), ui.dp(10))
  }

  /**
   * It scrolls when the screen is too short for it (a phone turned sideways), so Technical Logs is never cut off; on a
   * taller one it fills the height and Technical Logs sits at its bottom.
   */
  val view: ScrollView = ScrollView(context).apply {
    isFillViewport = true
    isVerticalScrollBarEnabled = false
    background = ui.rounded(ui.color(R.color.surface), 0)
    addView(column)
  }

  init {
    column.addView(LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(ui.dp(4), 0, ui.dp(4), ui.dp(10))
      addView(ImageView(context).apply {
        setImageResource(R.drawable.icon_cat)
        background = ui.rounded(ui.color(R.color.icon_background), 20)
        clipToOutline = true
      }, LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
      addView(ui.text(17f, R.color.ink, bold = true).apply {
        setText(R.string.app_name)
        setPadding(ui.dp(10), 0, 0, 0)
      }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    })
    for (section in Section.values().filter { !it.apart }) column.addView(item(context, section, onSelect), ui.fullWidth(2))
    column.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
    column.addView(View(context).apply { setBackgroundColor(ui.color(R.color.line)) }, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT, ui.dp(1),
    ).apply { setMargins(0, ui.dp(8), 0, ui.dp(8)) })
    for (section in Section.values().filter { it.apart }) column.addView(item(context, section, onSelect), ui.fullWidth())
  }

  private fun item(context: Context, section: Section, onSelect: (Section) -> Unit): View {
    val icon = ImageView(context).apply { setImageResource(section.icon()) }
    val label = ui.text(14f, R.color.muted).apply {
      setText(section.title())
      // "Versions and updates" takes two lines beside its icon, as on the desktop: set tight, as leading-tight is there.
      maxLines = 2
      setLineSpacing(0f, 1f)
    }
    val dot = View(context).apply {
      background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(ui.color(R.color.warn))
      }
      visibility = View.GONE
    }
    val root = LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      minimumHeight = ui.dp(48)
      setPadding(ui.dp(12), ui.dp(5), ui.dp(10), ui.dp(5))
      isClickable = true
      // Android 7 does not make a clickable view focusable by itself, and a remote reaches only what is.
      isFocusable = true
      contentDescription = context.getString(section.title())
      setOnClickListener { onSelect(section) }
      addView(icon, LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)))
      addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = ui.dp(12) })
      addView(FrameLayout(context).apply { addView(dot, FrameLayout.LayoutParams(ui.dp(9), ui.dp(9), Gravity.CENTER)) },
        LinearLayout.LayoutParams(ui.dp(9), ui.dp(9)).apply { leftMargin = ui.dp(8) })
    }
    items[section] = Item(root, icon, label, dot)
    return root
  }

  fun select(section: Section) {
    open = section
    for ((each, item) in items) {
      val here = each == section
      // The pill's corners are the ring's too (`FocusRing` reads the background's radius).
      val fill = if (here) ui.color(R.color.place_soft) else Color.TRANSPARENT
      item.root.background = RippleDrawable(ColorStateList.valueOf(ui.color(R.color.place_soft)), ui.rounded(fill, 24), null)
      item.icon.imageTintList = ColorStateList.valueOf(ui.color(if (here) R.color.place else R.color.muted))
      item.label.setTextColor(ui.color(if (here) R.color.place else R.color.muted))
      item.label.typeface = Fonts.typeface(item.label.context, if (here) Type.STRONG else Type.BODY)
      item.root.isSelected = here
    }
  }

  /** The dot on [section]: something there needs the person. */
  fun attention(section: Section, on: Boolean) {
    items[section]?.dot?.visibility = if (on) View.VISIBLE else View.GONE
  }

  /** The open section's item, where a remote enters the sidebar. */
  fun openItem(): View = items.getValue(open).root

  fun contains(view: View): Boolean {
    var at: View? = view
    while (at != null) {
      if (at === this.view) return true
      at = at.parent as? View
    }
    return false
  }
}

/**
 * The sidebar beside the page. A remote's Left from the page enters the sidebar at the open section, not at whichever
 * item lies nearest; Up and Down stay on their side, in the sidebar or in the page. Right leaves the sidebar for the
 * page's nearest button, as Android finds it ([SidebarFocus]).
 */
internal class SidebarLayout(context: Context, private val sidebar: Sidebar, line: View, private val page: View, sidebarWidthPx: Int) :
  LinearLayout(context) {
  init {
    orientation = HORIZONTAL
    addView(sidebar.view, LayoutParams(sidebarWidthPx, LayoutParams.MATCH_PARENT))
    addView(line, LayoutParams(Ui(context).dp(1), LayoutParams.MATCH_PARENT))
    addView(page, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
  }

  override fun focusSearch(focused: View?, direction: Int): View? {
    if (focused == null || sidebar.view.visibility != View.VISIBLE) return super.focusSearch(focused, direction)
    val inSidebar = sidebar.contains(focused)
    val vertical = direction == View.FOCUS_UP || direction == View.FOCUS_DOWN
    if (SidebarFocus.next(inSidebar, direction == View.FOCUS_LEFT, vertical, foundInSidebar = null) == SidebarFocus.OWN_SIDE) {
      val side = (if (inSidebar) sidebar.view else page) as ViewGroup
      return FocusFinder.getInstance().findNextFocus(side, focused, direction) ?: focused
    }
    val found = super.focusSearch(focused, direction)
    return when (SidebarFocus.next(inSidebar, direction == View.FOCUS_LEFT, vertical, found?.let(sidebar::contains))) {
      SidebarFocus.OPEN_SECTION -> sidebar.openItem()
      else -> found
    }
  }
}

/** Where a remote's key goes between the sidebar and the page. Pure, so the rules are tested without a screen. */
internal enum class SidebarFocus {
  OPEN_SECTION, OWN_SIDE, AS_FOUND;

  companion object {
    /**
     * [fromSidebar]: focus is in the sidebar now. [foundInSidebar]: where Android's own search over the whole screen
     * would go, in the sidebar (true), in the page (false) or nowhere (null). Up and Down look only on the side focus is
     * on, and stay at its ends: over the whole screen, Down from a page's last button reached Technical Logs, and Down
     * from Settings preferred a nearer button in the page to Technical Logs (both on the TV). Left into the sidebar
     * lands on the open section. Right is Android's own.
     */
    fun next(fromSidebar: Boolean, left: Boolean, vertical: Boolean, foundInSidebar: Boolean?): SidebarFocus = when {
      vertical -> OWN_SIDE
      !fromSidebar && left && foundInSidebar == true -> OPEN_SECTION
      else -> AS_FOUND
    }
  }
}

internal fun Section.title(): Int = when (this) {
  Section.HOME -> R.string.tab_home
  Section.CONNECT -> R.string.tab_connect
  Section.VERSIONS -> R.string.versions_title
  Section.SETTINGS -> R.string.settings_title
  Section.LOGS -> R.string.support_title
}

internal fun Section.icon(): Int = when (this) {
  Section.HOME -> R.drawable.ic_tab_home
  Section.CONNECT -> R.drawable.ic_tab_connect
  Section.VERSIONS -> R.drawable.ic_section_versions
  Section.SETTINGS -> R.drawable.ic_tab_more
  Section.LOGS -> R.drawable.ic_section_logs
}
