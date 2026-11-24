package tech.nouverse.nouride.android.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** A remote's D-pad shows where it is on every screen (`FocusRing`): read from the sources, as no screen is drawn here. */
class FocusRingTest {
  private val src = listOf(File("src"), File("app/src")).first { it.isDirectory }

  @Test
  fun `every screen rings what the D-pad reaches`() {
    val screens = File(src, "main/kotlin").walk().filter { it.extension == "kt" }
      .filter { Regex("""class \w+\s*:\s*Activity\(\)""").containsMatchIn(it.readText()) }.toList()
    assertTrue(screens.size >= 5)
    for (screen in screens) {
      assertTrue("${screen.name} does not call FocusRing.follow", screen.readText().contains("FocusRing.follow(this)"))
    }
  }
}
