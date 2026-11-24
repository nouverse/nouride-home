package tech.nouverse.nouride.android.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import tech.nouverse.nouride.android.R
import tech.nouverse.nouride.android.power.KeepRunning
import tech.nouverse.nouride.android.power.PowerSettings
import tech.nouverse.nouride.android.Layout
import tech.nouverse.nouride.android.server.DeviceSupport
import tech.nouverse.nouride.android.server.HealthPoller
import tech.nouverse.nouride.android.server.Place
import tech.nouverse.nouride.android.server.RemoteReply
import tech.nouverse.nouride.android.server.Reply
import tech.nouverse.nouride.android.server.ServerService
import tech.nouverse.nouride.android.server.ServerState
import tech.nouverse.nouride.android.server.Where
import tech.nouverse.nouride.android.update.NourideNotice
import tech.nouverse.nouride.android.update.Updates
import kotlin.concurrent.thread

/**
 * The app's screen, three tabs along a bar at the bottom: Home (the cat, how Nouride is, one big button), Connect
 * (Nouride on another device) and More (keeping it running, this app's version, turning it off, Technical Logs). The
 * users are not technical, so the server's phases, addresses and log stay behind Technical Logs (`SupportActivity`,
 * `StatusLook`, and the home-ui skill). The bar is hidden while the first start sets Nouride up.
 *
 * A phone without Nouride first asks where it runs (`Place`): on this phone, started when that is pressed, or on another
 * computer at an address the person gives. Nothing of the phone's own server starts until it is answered, and answered
 * "another computer" this screen never starts one at all. More changes the answer either way.
 *
 * On a wide screen (720 dp and up: a tablet, a TV, a phone turned sideways) the bar gives way to the desktop's sidebar,
 * and More's cards become its sections: Versions and updates, Settings and Technical Logs (`Sections`,
 * `spec/layout.md`). The shape is read from the window's width as the screen is made; a change of size (turning the
 * phone, splitting the screen) makes it again, and the open section carries over.
 *
 * The extras `start`, `stop` and `dashboard` (booleans) do what the buttons do, and `tab` (home, connect, more, or a
 * section: versions, settings, logs) opens it, for `adb shell am start --ez <name> true` and `--es tab connect`.
 */
class StatusActivity : Activity() {
  private lateinit var ui: Ui
  private lateinit var update: UpdateCard
  private lateinit var nourideUpdate: NourideUpdateCard
  private lateinit var home: HomeTab
  private lateinit var connect: ConnectTab
  private lateinit var more: MoreTab
  private lateinit var place: Place
  private lateinit var shape: Shape

  /** The phone's bar, or on a wide screen the sidebar and the sections More holds on a phone. */
  private var bar: BottomBar? = null
  private var sidebar: Sidebar? = null
  private var versions: VersionsPage? = null
  private var logs: LogsPage? = null
  private val pages = LinkedHashMap<Section, View>()
  private var section = Section.HOME
  private var resumed = false
  private val handler = Handler(Looper.getMainLooper())
  private val listener: () -> Unit = {
    render()
    update.serverChanged()
    askNouride()
  }
  private val refresh = object : Runnable {
    override fun run() {
      render()
      handler.postDelayed(this, 2_000)
    }
  }

  /** In the appearance chosen on the More tab (`Appearances`). */
  override fun attachBaseContext(base: Context) = super.attachBaseContext(Appearances.wrap(base))

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    place = Place(this)
    ui = Ui(this)
    shape = Sections.shape(resources.configuration.screenWidthDp)
    ui.lightSystemBars(window)
    setContentView(views())
    FocusRing.follow(this)
    show(savedInstanceState?.getString(STATE_SECTION)?.let(Sections::named) ?: Section.HOME)
    // A remote starts at the open section, as the desktop's sidebar does (only out of touch mode: a phone never shows it).
    sidebar?.openItem()?.requestFocus()
    if (savedInstanceState == null) handleExtras(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleExtras(intent)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putString(STATE_SECTION, section.name)
  }

  private fun handleExtras(intent: Intent) {
    if (intent.getBooleanExtra(EXTRA_START, false)) startServer()
    if (intent.getBooleanExtra(EXTRA_STOP, false)) ServerService.stop(this)
    if (intent.getBooleanExtra(EXTRA_DASHBOARD, false)) openDashboard()
    intent.getStringExtra(EXTRA_TAB)?.let(Sections::named)?.let(::show)
  }

  override fun onResume() {
    super.onResume()
    resumed = true
    pageShown()
    ServerState.listen(listener)
    handler.post(refresh)
    update.resume()
    more.resume()
    render()
    checkRemote()
    nourideAsked = false
    askNouride()
  }

  override fun onPause() {
    resumed = false
    versions?.pause()
    ServerState.unlisten(listener)
    handler.removeCallbacks(refresh)
    super.onPause()
  }

  override fun onDestroy() {
    versions?.close()
    super.onDestroy()
  }

  @Deprecated("Activity.onBackPressed")
  override fun onBackPressed() {
    val to = if (shape.sidebar) Sections.back(section) else Tabs.back(Sections.tab(section))?.let(Sections::of)
    @Suppress("DEPRECATION")
    if (to == null) super.onBackPressed() else show(to)
  }

  /** Never while Nouride lives elsewhere: there is no server of this phone's to start. */
  private fun startServer() {
    if (place.where() == Where.REMOTE) return openDashboard()
    // Said on Home at once, rather than asking for the battery exemption of something that cannot run.
    if (!runsHere()) return ServerState.set(ServerState.Phase.UNSUPPORTED, progress = null)
    update.starting()
    ServerService.start(this)
    if (PowerSettings.keepRunning(this) == KeepRunning.ASK) startActivity(PowerSettings.exemptionRequest(this))
  }

  private fun runsHere(): Boolean = DeviceSupport.runs(Layout(this).prootPaths().proot)

  private fun openDashboard() = startActivity(Intent(this, DashboardActivity::class.java))

  private fun views(): View {
    // More's Versions line says what the check found, not what was known before it.
    update = UpdateCard(this, ui, onChecked = { more.resume() })
    nourideUpdate = NourideUpdateCard(this, ui, ::updateNouride)
    home = HomeTab(
      this, ui, shape, update, nourideUpdate,
      onPrimary = ::onPrimary,
      onKeepRunning = { show(Section.SETTINGS) },
      onRunHere = ::runOnThisPhone,
      onConnectRemote = ::askForAddress,
    )
    connect = ConnectTab(this, ui, shape, onGoHome = { show(Section.HOME) })
    more = MoreTab(this, ui, shape, onTurnOff = ::confirmTurnOff, onChoose = ::choose, onChangeAddress = ::askForAddress)
    pages[Section.HOME] = home.view
    pages[Section.CONNECT] = connect.view
    pages[Section.SETTINGS] = more.view
    val content = FrameLayout(this)
    if (!shape.sidebar) {
      val bar = BottomBar(this, ui, onSelect = { show(Sections.of(it)) })
      this.bar = bar
      for (page in pages.values) content.addView(page)
      return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = ui.page()
        addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(bar.view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
          setMargins(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(12))
        })
      }
    }
    val versions = VersionsPage(this, ui, twoColumns = shape == Shape.TWO_COLUMNS).also { this.versions = it }
    val logs = LogsPage(this, ui, wide = true).also { this.logs = it }
    pages[Section.VERSIONS] = ui.scroll(ui.column(shape).apply {
      addView(ui.pageTitle(R.string.versions_title))
      addView(versions.cards, ui.fullWidth(16))
    }, shape)
    // Technical Logs takes the whole page: its log scrolls in its own box.
    pages[Section.LOGS] = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(ui.dp(12), ui.dp(24), ui.dp(12), ui.dp(12))
      addView(ui.pageTitle(R.string.support_title).apply { setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(16)) })
      addView(logs.view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }
    for (section in Section.values()) content.addView(pages.getValue(section))
    val sidebar = Sidebar(this, ui, onSelect = ::show).also { this.sidebar = it }
    val line = View(this).apply { setBackgroundColor(ui.color(R.color.line)) }
    return SidebarLayout(this, sidebar, line, content, ui.dp(SIDEBAR_DP)).apply { background = ui.page() }
  }

  /** Home's card for a new Nouride: Versions and updates, where its update starts and is followed. */
  private fun updateNouride() {
    val versions = versions ?: return startActivity(
      Intent(this, VersionsActivity::class.java).putExtra(VersionsActivity.EXTRA_UPDATE_NOURIDE, true),
    )
    versions.updateNourideWhenFound()
    show(Section.VERSIONS)
  }

  /** On a phone, a section of More shows More; Versions and Technical Logs open from its rows, as screens of their own. */
  private fun show(next: Section) {
    val before = section
    section = if (shape.sidebar) next else Sections.of(Sections.tab(next))
    for ((each, page) in pages) page.visibility = if (each == section) View.VISIBLE else View.GONE
    bar?.select(Sections.tab(section))
    sidebar?.select(section)
    if (before != section) pageShown(left = before)
    render()
    // A page's button that opened another section (Go to Home, the battery line) is gone: the remote goes on from the
    // sidebar, at the section now open.
    val focused = currentFocus
    if (focused == null || !focused.isShown) sidebar?.openItem()?.requestFocus()
  }

  /** The Versions and Technical Logs pages read what they show as they come into view, and stop when they leave it. */
  private fun pageShown(left: Section? = null) {
    if (left == Section.VERSIONS) versions?.pause()
    if (!resumed) return
    when (section) {
      Section.VERSIONS -> versions?.resume()
      Section.LOGS -> logs?.load()
      else -> Unit
    }
  }

  private fun render() {
    val where = place.where()
    val address = place.address()
    val look = if (where == Where.REMOTE) StatusLook.remote(reply)
    else StatusLook.of(ServerState.phase, answering = ServerState.health?.ok == true)
    val keepRunning = PowerSettings.keepRunning(this)
    val mayStop = where == Where.PHONE && keepRunning == KeepRunning.ASK
    val barShown = Tabs.barShown(look, firstStart = ServerState.progress != null)
    val sidebarShown = Sections.sidebarShown(placeChosen = where != null, barShown = barShown)
    if (!barShown && section != Section.HOME) return show(Section.HOME)
    if (shape.sidebar && !sidebarShown && section != Section.HOME) return show(Section.HOME)
    bar?.view?.visibility = if (barShown) View.VISIBLE else View.GONE
    sidebar?.let { sidebar ->
      val was = sidebar.view.visibility
      sidebar.view.visibility = if (sidebarShown) View.VISIBLE else View.GONE
      (sidebar.view.parent as ViewGroup).getChildAt(1).visibility = sidebar.view.visibility
      // The sidebar has just come (the first question answered, the first setup over): a remote starts on it.
      if (was != View.VISIBLE && sidebarShown) handler.post { if (currentFocus?.isShown != true) sidebar.openItem().requestFocus() }
    }
    home.alone(!sidebarShown)
    // Nouride's own update, once it answers and the first start is over: the card's Update needs it running.
    val nourideReady = NourideNotice.waiting(this)
      ?.takeIf { where == Where.PHONE && ServerState.health?.ok == true && ServerState.progress == null }
    nourideUpdate.render(nourideReady)
    val appWaits = Updates.waiting(this) != null
    bar?.attention(Tab.MORE, UpdateNotices.moreDot(mayStop, app = appWaits, nouride = nourideReady != null))
    for (each in Section.values()) sidebar?.attention(each, Sections.dot(each, mayStop, app = appWaits, nouride = nourideReady != null))
    home.render(look, mayStop, where, address, runsHere())
    if (section == Section.CONNECT) connect.render(look, where, address)
    more.render(keepRunning, where != Where.REMOTE && StatusLook.canTurnOff(ServerState.phase), where, address)
  }

  private fun onPrimary() {
    if (place.where() == Where.REMOTE) {
      // Open while that computer may answer; Try again when it did not.
      return if (StatusLook.remote(reply).action == Action.RETRY) checkRemote(again = true) else openDashboard()
    }
    when (StatusLook.of(ServerState.phase, ServerState.health?.ok == true).action) {
      Action.START, Action.RETRY -> startServer()
      Action.OPEN -> openDashboard()
      Action.ELSEWHERE -> askForAddress()
      Action.NONE -> Unit
    }
  }

  /**
   * "Run it on this phone", on the first screen or on More: the app as it always was, started at once, as the choice
   * is the person asking for it. The install already here is picked up as it was.
   */
  private fun runOnThisPhone() {
    place.onThisPhone()
    reply = null
    show(Section.HOME)
    if (ServerState.phase == ServerState.Phase.STOPPED || ServerState.phase == ServerState.Phase.FAILED) startServer()
  }

  /** More's choice: this phone at once; another computer only once its address is given and answers. */
  private fun choose(where: Where) {
    if (where == place.where()) return
    if (where == Where.PHONE) runOnThisPhone() else askForAddress()
  }

  /** The address of a Nouride on another computer, on its own screen (`AddressActivity`); back here, [onActivityResult]. */
  private fun askForAddress() {
    startActivityForResult(Intent(this, AddressActivity::class.java), REQUEST_ADDRESS)
  }

  /**
   * An address given and answering: Nouride lives there now, and Home says how it is. Anything this phone was running
   * is turned off, so a phone that has moved on does not keep two; the address screen said so before. Cancel or back
   * leaves the screen as it was, on the first screen or on More.
   */
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    val base = data?.getStringExtra(AddressActivity.EXTRA_BASE)
    if (requestCode != REQUEST_ADDRESS || resultCode != RESULT_OK || base == null) return
    place.onMachine(base)
    if (ServerState.phase != ServerState.Phase.STOPPED) ServerService.stop(this)
    show(Section.HOME)
    checkRemote(again = true)
  }

  /** Asked once each time the screen comes back, as soon as Nouride answers (`NourideNotice.check` keeps it to 15 minutes). */
  private var nourideAsked = false

  private fun askNouride() {
    if (nourideAsked || place.where() != Where.PHONE || ServerState.health?.ok != true) return
    nourideAsked = true
    thread(name = "nouride-update-notice") {
      NourideNotice.check(this, Updates.ON_OPEN_MS)
      handler.post {
        render()
        more.resume()
      }
    }
  }

  /** What the other computer's Nouride says (`RemoteReply`), asked when the screen comes back and on Try again. */
  private var reply: Reply? = null
  private var replyFor: String? = null
  private var asking = 0

  /**
   * [again]: the person asked (Try again, a new address), so the card says it is checking. Coming back to the screen
   * keeps the last answer up until the new one is in, rather than flicker through "checking" each time.
   */
  private fun checkRemote(again: Boolean = false) {
    val base = place.address() ?: return
    val ask = ++asking
    if (again || replyFor != base) {
      reply = null
      render()
    }
    thread(name = "remote-health") {
      val answer = RemoteReply.of(HealthPoller(RemoteReply.healthUrl(base), 0) {}.check())
      handler.post {
        // A later question, or another address, owns the screen now.
        if (ask != asking || place.address() != base) return@post
        reply = answer
        replyFor = base
        render()
      }
    }
  }

  private fun confirmTurnOff() {
    AlertDialog.Builder(this)
      .setTitle(R.string.turn_off_title)
      .setMessage(R.string.turn_off_body)
      .setPositiveButton(R.string.turn_off) { _, _ -> ServerService.stop(this) }
      .setNegativeButton(R.string.cancel, null)
      .show()
  }

  companion object {
    const val EXTRA_START = "start"
    const val EXTRA_STOP = "stop"
    const val EXTRA_DASHBOARD = "dashboard"
    const val EXTRA_TAB = "tab"
    private const val STATE_SECTION = "section"

    /** The sidebar's width with its labels, the desktop's 228 px. */
    private const val SIDEBAR_DP = 228
    private const val REQUEST_ADDRESS = 1
  }
}
