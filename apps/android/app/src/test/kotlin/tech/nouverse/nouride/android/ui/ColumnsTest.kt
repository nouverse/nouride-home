package tech.nouverse.nouride.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The columns' arithmetic (`Columns.split`): nothing overlaps, and nothing is wider than the page. */
class ColumnsTest {
  @Test
  fun `side by side, the gap between, the whole width used and nothing over it`() {
    for (width in listOf(600, 676, 900, 1800)) {
      for (weight in listOf(0.4f, 0.5f, 0.535f)) {
        val s = Columns.split(width, 40, weight, 1200, secondShown = true)
        assertEquals(0, s.firstLeft)
        assertEquals(s.firstWidth + 40, s.secondLeft)
        assertEquals(width, s.secondLeft + s.secondWidth)
        assertTrue(s.firstWidth > 0 && s.secondWidth > 0)
      }
    }
  }

  @Test
  fun `with nothing beside it, the first stands alone in the middle, at most its own width`() {
    assertEquals(Columns.Split(300, 1200, 0, 0), Columns.split(1800, 40, 0.5f, 1200, secondShown = false))
    // Narrower than its most: the whole width, from the left edge.
    assertEquals(Columns.Split(0, 900, 0, 0), Columns.split(900, 40, 0.5f, 1200, secondShown = false))
  }

  @Test
  fun `a page keeps its readable width and never grows past the screen`() {
    assertEquals(2640, Columns.readable(4000, 2640))
    assertEquals(1500, Columns.readable(1500, 2640))
  }
}
