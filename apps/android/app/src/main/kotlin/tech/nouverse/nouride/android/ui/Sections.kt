package tech.nouverse.nouride.android.ui

/**
 * How the app's screen is drawn at the window's width (`spec/layout.md`): the phone's bottom bar, the desktop's sidebar
 * with one column, or the sidebar with two columns where a page has them.
 */
enum class Shape {
  PHONE, SIDEBAR, TWO_COLUMNS;

  val sidebar: Boolean get() = this != PHONE
}

/**
 * The sidebar's sections on a wide screen, in its order, as the desktop's: siblings, none inside another. On a phone
 * the last three are folded into More (`Tab.MORE`).
 */
enum class Section(val apart: Boolean = false) {
  HOME, CONNECT, VERSIONS, SETTINGS,

  /** Technical Logs, set apart at the bottom, for whoever helps. */
  LOGS(apart = true),
}

/** Which shape a width takes, and which section is open in either shape. Pure, so the rules are tested without a screen. */
object Sections {
  /** From this window width in dp the sidebar takes the bottom bar's place. */
  const val SIDEBAR_FROM_DP = 720

  /** From this window width in dp the pages that have two columns use them. */
  const val TWO_COLUMNS_FROM_DP = 960

  /** The widest a page grows on a wide screen, centred; Settings keeps to [SETTINGS_READABLE_DP]. */
  const val READABLE_DP = 960
  const val SETTINGS_READABLE_DP = 720

  /** [widthDp]: the window's width now (`Configuration.screenWidthDp`), which turning a phone or splitting the screen changes. */
  fun shape(widthDp: Int): Shape = when {
    widthDp < SIDEBAR_FROM_DP -> Shape.PHONE
    widthDp < TWO_COLUMNS_FROM_DP -> Shape.SIDEBAR
    else -> Shape.TWO_COLUMNS
  }

  /** The phone's tab a section is in. */
  fun tab(section: Section): Tab = when (section) {
    Section.HOME -> Tab.HOME
    Section.CONNECT -> Tab.CONNECT
    Section.VERSIONS, Section.SETTINGS, Section.LOGS -> Tab.MORE
  }

  /** The section a phone's tab is on a wide screen: More's own cards are Settings. */
  fun of(tab: Tab): Section = when (tab) {
    Tab.HOME -> Section.HOME
    Tab.CONNECT -> Section.CONNECT
    Tab.MORE -> Section.SETTINGS
  }

  /** A tab's or a section's name (`--es tab ...`): `more` is Settings, as the phone's More is. */
  fun named(name: String): Section? =
    if (name.equals("more", ignoreCase = true)) Section.SETTINGS else Section.values().firstOrNull { it.name.equals(name, ignoreCase = true) }

  /** Siblings: Back from any section goes Home, as the phone's bar does; from Home it leaves the app. */
  fun back(from: Section): Section? = if (from == Section.HOME) null else Section.HOME

  /**
   * No sidebar before the first question is answered, nor while the first setup runs ([barShown], `Tabs.barShown`):
   * the question, or the setup's progress, stands alone in the middle, as on the desktop.
   */
  fun sidebarShown(placeChosen: Boolean, barShown: Boolean): Boolean = placeChosen && barShown

  /**
   * The dot on a section: on Versions and updates while an update waits there, on Settings while the phone may stop
   * Nouride with the screen off, which is fixed there. The phone's More carries both (`UpdateNotices.moreDot`).
   */
  fun dot(section: Section, mayStop: Boolean, app: Boolean, nouride: Boolean): Boolean = when (section) {
    Section.VERSIONS -> app || nouride
    Section.SETTINGS -> mayStop
    else -> false
  }
}
