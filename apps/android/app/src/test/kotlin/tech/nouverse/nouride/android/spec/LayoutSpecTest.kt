package tech.nouverse.nouride.android.spec

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import tech.nouverse.nouride.android.ui.Section
import tech.nouverse.nouride.android.ui.Sections
import tech.nouverse.nouride.android.ui.Shape

/** The shape at each width and the sections' order, from `spec/fixtures/layout.json`, which the desktop's tests read too. */
class LayoutSpecTest {
  private val spec = Spec.fixture("layout.json")

  @Test
  fun `the breakpoints and readable widths are the spec's`() {
    assertEquals(spec.getInt("sidebar_from"), Sections.SIDEBAR_FROM_DP)
    assertEquals(spec.getInt("two_columns_from"), Sections.TWO_COLUMNS_FROM_DP)
    assertEquals(spec.getJSONObject("readable").getInt("page"), Sections.READABLE_DP)
    assertEquals(spec.getJSONObject("readable").getInt("settings"), Sections.SETTINGS_READABLE_DP)
  }

  @Test
  fun `the sections, in the spec's order, with only Technical Logs set apart`() {
    val sections = Spec.cases(spec, "sections").map { it.getString("section") to it.getBoolean("apart") }
    assertEquals(sections, Section.values().map { it.name.lowercase() to it.apart })
  }

  @Test
  fun `the shape at each width is the spec's`() {
    for (case in Spec.cases(spec, "widths")) {
      val want = Spec.expected(case) as JSONObject
      val shape = Sections.shape(case.getInt("width"))
      val sidebar = if (shape.sidebar) "labels" else "none"
      val columns = if (shape == Shape.TWO_COLUMNS) 2 else 1
      assertEquals("$case", want.getString("sidebar") to want.getInt("columns"), sidebar to columns)
    }
  }
}
