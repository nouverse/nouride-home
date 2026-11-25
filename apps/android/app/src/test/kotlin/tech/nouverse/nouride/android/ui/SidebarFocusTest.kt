package tech.nouverse.nouride.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Where a remote's key goes between the sidebar and the page (`SidebarLayout`). */
class SidebarFocusTest {
  private fun next(fromSidebar: Boolean, key: String, found: Boolean?) =
    SidebarFocus.next(fromSidebar, left = key == "left", vertical = key == "up" || key == "down", foundInSidebar = found)

  @Test
  fun `left from the page enters the sidebar at the open section`() {
    assertEquals(SidebarFocus.OPEN_SECTION, next(fromSidebar = false, key = "left", found = true))
    // Left inside the page (from its second column to its first) is Android's own.
    assertEquals(SidebarFocus.AS_FOUND, next(fromSidebar = false, key = "left", found = false))
  }

  @Test
  fun `up and down look only on their own side, whatever lies nearer across`() {
    // On the TV, Down from Connect's Share address reached Technical Logs, and Down from Settings preferred a
    // button in the page to Technical Logs.
    for (fromSidebar in listOf(true, false)) for (key in listOf("up", "down")) for (found in listOf(true, false, null)) {
      assertEquals("$fromSidebar $key $found", SidebarFocus.OWN_SIDE, next(fromSidebar, key, found))
    }
  }

  @Test
  fun `right leaves the sidebar for the page as Android finds it`() {
    assertEquals(SidebarFocus.AS_FOUND, next(fromSidebar = true, key = "right", found = false))
  }
}
