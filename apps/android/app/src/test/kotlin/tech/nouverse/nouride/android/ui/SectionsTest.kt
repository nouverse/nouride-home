package tech.nouverse.nouride.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionsTest {
  @Test
  fun `a phone's width keeps the bottom bar, a wide one the sidebar, the widest two columns`() {
    assertEquals(Shape.PHONE, Sections.shape(360))
    assertEquals(Shape.PHONE, Sections.shape(719))
    assertEquals(Shape.SIDEBAR, Sections.shape(720))
    assertEquals(Shape.SIDEBAR, Sections.shape(959))
    assertEquals(Shape.TWO_COLUMNS, Sections.shape(960))
    assertEquals(Shape.TWO_COLUMNS, Sections.shape(1280))
  }

  @Test
  fun `a section and a tab carry over when the phone turns`() {
    assertEquals(Tab.HOME, Sections.tab(Section.HOME))
    assertEquals(Tab.CONNECT, Sections.tab(Section.CONNECT))
    for (section in listOf(Section.VERSIONS, Section.SETTINGS, Section.LOGS)) assertEquals(Tab.MORE, Sections.tab(section))
    assertEquals(Section.HOME, Sections.of(Tab.HOME))
    assertEquals(Section.CONNECT, Sections.of(Tab.CONNECT))
    assertEquals(Section.SETTINGS, Sections.of(Tab.MORE))
  }

  @Test
  fun `a name opens its section, and More is Settings`() {
    assertEquals(Section.SETTINGS, Sections.named("more"))
    assertEquals(Section.VERSIONS, Sections.named("versions"))
    assertEquals(Section.LOGS, Sections.named("LOGS"))
    assertNull(Sections.named("dashboard"))
  }

  @Test
  fun `back from any section goes Home, and from Home it leaves`() {
    for (section in Section.values().filter { it != Section.HOME }) assertEquals(Section.HOME, Sections.back(section))
    assertNull(Sections.back(Section.HOME))
  }

  @Test
  fun `no sidebar before the first question is answered, nor while the first setup runs`() {
    assertFalse(Sections.sidebarShown(placeChosen = false, barShown = true))
    assertFalse(Sections.sidebarShown(placeChosen = true, barShown = false))
    assertTrue(Sections.sidebarShown(placeChosen = true, barShown = true))
  }

  @Test
  fun `the dot is on Versions for an update and on Settings for the battery, never elsewhere`() {
    assertTrue(Sections.dot(Section.VERSIONS, mayStop = false, app = true, nouride = false))
    assertTrue(Sections.dot(Section.VERSIONS, mayStop = false, app = false, nouride = true))
    assertFalse(Sections.dot(Section.VERSIONS, mayStop = true, app = false, nouride = false))
    assertTrue(Sections.dot(Section.SETTINGS, mayStop = true, app = false, nouride = false))
    assertFalse(Sections.dot(Section.SETTINGS, mayStop = false, app = true, nouride = true))
    for (section in listOf(Section.HOME, Section.CONNECT, Section.LOGS)) {
      assertFalse(Sections.dot(section, mayStop = true, app = true, nouride = true))
    }
  }
}
