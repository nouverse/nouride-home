package tech.nouverse.nouride.android.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * On a TV the app's words say "this TV" (`res/values-television`, which Android reads in UI mode television): every
 * sentence that names this phone has its TV form, the TV's never names a phone as this device, and nothing there is
 * a sentence the phone does not have.
 */
class TelevisionWordsTest {
  private val res = listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

  private fun strings(folder: String): Map<String, String> {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings.xml"))
    val nodes = doc.getElementsByTagName("string")
    return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
  }

  /** The device itself called a phone: "this phone", "the phone", "your phone", "Same as phone". */
  private val namesThisPhone = Regex("""\b(this|the|your|as) phone\b""", RegexOption.IGNORE_CASE)

  /** Things a person does only on a phone: tapping, a battery, Wi-Fi as the only way onto the network. */
  private val phoneOnly = Regex("""\btap\b|\bbattery\b|\bthe Wi-Fi\b""", RegexOption.IGNORE_CASE)

  @Test
  fun `every sentence that names this phone has its TV form`() {
    val phone = strings("values")
    val tv = strings("values-television")
    val missing = phone.filter { (name, text) ->
      (namesThisPhone.containsMatchIn(text) || phoneOnly.containsMatchIn(text)) && name !in tv && name !in PHONE_ONLY
    }.keys
    assertEquals(emptySet<String>(), missing)
  }

  @Test
  fun `the TV's words never call this device a phone`() {
    for ((name, text) in strings("values-television")) {
      assertTrue("$name: $text", !namesThisPhone.containsMatchIn(text) && !phoneOnly.containsMatchIn(text))
    }
  }

  @Test
  fun `the TV changes words, never adds a sentence`() {
    val phone = strings("values")
    for (name in strings("values-television").keys) assertTrue("$name is not in values/strings.xml", name in phone)
  }

  private companion object {
    /** Shown only on a Xiaomi phone: its Autostart list. */
    val PHONE_ONLY = setOf("autostart_body")
  }
}
