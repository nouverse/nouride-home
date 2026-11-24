package tech.nouverse.nouride.android.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Nouride Home on Android TV and Google TV: shown in the TV launcher by its banner, and installable without a touch
 * screen. Read from the sources.
 */
class TelevisionTest {
  private val src = listOf(File("src"), File("app/src")).first { it.isDirectory }
  private val android = "http://schemas.android.com/apk/res/android"

  private fun manifest() = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    .newDocumentBuilder().parse(File(src, "main/AndroidManifest.xml")).documentElement

  private fun Element.children(tag: String): List<Element> =
    (0 until getElementsByTagName(tag).length).map { getElementsByTagName(tag).item(it) as Element }

  @Test
  fun `the screen the launcher opens is in the TV launcher too`() {
    val launched = manifest().children("activity").filter { activity ->
      activity.children("category").any { it.getAttributeNS(android, "name") == "android.intent.category.LAUNCHER" }
    }
    assertEquals(1, launched.size)
    val categories = launched.single().children("category").map { it.getAttributeNS(android, "name") }
    assertTrue(categories.toString(), "android.intent.category.LEANBACK_LAUNCHER" in categories)
  }

  @Test
  fun `neither a TV nor a touch screen is required, so phones and TVs both install it`() {
    val features = manifest().children("uses-feature").associate {
      it.getAttributeNS(android, "name") to it.getAttributeNS(android, "required")
    }
    assertEquals("false", features["android.software.leanback"])
    assertEquals("false", features["android.hardware.touchscreen"])
  }

  @Test
  fun `the release and the Dev build each have a 320 by 180 banner`() {
    val application = manifest().children("application").single()
    assertEquals("@drawable/banner", application.getAttributeNS(android, "banner"))
    for (build in listOf("main", "debug")) {
      // A PNG's size is in its IHDR chunk, right after the signature: width then height, 4 bytes each.
      val png = File(src, "$build/res/drawable-xhdpi/banner.png").readBytes()
      val size = java.nio.ByteBuffer.wrap(png, 16, 8)
      assertEquals("$build width", 320, size.int)
      assertEquals("$build height", 180, size.int)
    }
  }
}
