package app.nebula.vpn

import app.nebula.vpn.core.tor.BridgeLineParser
import app.nebula.vpn.core.tor.BridgeTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BridgeHonestyLocalizationTest {

    @Test
    fun testPluggableTransportsAreRejectedWithHonestMessage() {
        val obfs4 = BridgeLineParser.parseLine("obfs4 192.0.2.1:443 1234567890ABCDEF1234567890ABCDEF12345678 cert=xyz iat-mode=0")
        assertFalse("obfs4 line must not be valid", obfs4.isValid)
        assertFalse("obfs4 line must not be marked supported", obfs4.isSupported)
        assertTrue(obfs4.errorReason?.contains("not supported yet") == true)

        val webtunnel = BridgeLineParser.parseLine("webtunnel 192.0.2.2:443 1234567890ABCDEF1234567890ABCDEF12345678 url=https://example.com/wt")
        assertFalse("webtunnel line must not be valid", webtunnel.isValid)
        assertFalse("webtunnel line must not be marked supported", webtunnel.isSupported)
        assertTrue(webtunnel.errorReason?.contains("not supported yet") == true)

        val snowflake = BridgeLineParser.parseLine("snowflake 192.0.2.3:1 1234567890ABCDEF1234567890ABCDEF12345678")
        assertFalse("snowflake line must not be valid", snowflake.isValid)
        assertFalse("snowflake line must not be marked supported", snowflake.isSupported)
        assertTrue(snowflake.errorReason?.contains("not supported yet") == true)
    }

    @Test
    fun testVanillaBridgesAreAccepted() {
        val vanilla = BridgeLineParser.parseLine("192.0.2.1:443 1234567890ABCDEF1234567890ABCDEF12345678")
        assertTrue("Vanilla bridge must be valid", vanilla.isValid)
        assertTrue("Vanilla bridge must be supported", vanilla.isSupported)
        assertEquals(BridgeTransport.VANILLA, vanilla.transport)
    }

    @Test
    fun testHonestBridgeInstructionsAcrossAllFiveLocales() {
        val locales = listOf(
            "en" to File("src/main/res/values/strings.xml"),
            "ru" to File("src/main/res/values-ru/strings.xml"),
            "uk" to File("src/main/res/values-uk/strings.xml"),
            "de" to File("src/main/res/values-de/strings.xml"),
            "zh-CN" to File("src/main/res/values-zh-rCN/strings.xml")
        )

        for ((locale, file) in locales) {
            assertTrue("File for $locale must exist", file.exists())
            val stringsMap = extractStrings(file)

            val unsupportedMsg = stringsMap["tor_bridge_transport_unsupported"]
            assertTrue("Locale [$locale] must define tor_bridge_transport_unsupported", !unsupportedMsg.isNullOrBlank())

            val dialogMsg = stringsMap["tor_bridge_get_dialog_message"]
            assertTrue("Locale [$locale] must define tor_bridge_get_dialog_message", !dialogMsg.isNullOrBlank())

            assertFalse(
                "Locale [$locale] must NOT instruct users to fetch obfs4/webtunnel from @GetBridgesBot",
                dialogMsg!!.contains("@GetBridgesBot")
            )

            assertTrue(
                "Locale [$locale] must guide users to vanilla bridges or carrier mode",
                dialogMsg.contains("vanilla", ignoreCase = true) || dialogMsg.contains("Vanilla")
            )
        }
    }

    private fun extractStrings(file: File): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")
        val map = mutableMapOf<String, String>()
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            val name = node.attributes.getNamedItem("name")?.nodeValue
            if (name != null) {
                map[name] = node.textContent
            }
        }
        return map
    }
}
