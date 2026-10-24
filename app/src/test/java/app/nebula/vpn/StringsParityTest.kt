package app.nebula.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringsParityTest {

    @Test
    fun testStringsParityAcrossAllFiveLanguages() {
        val rootDir = File("src/main/res")
        val defaultStringsFile = File(rootDir, "values/strings.xml")
        val ruStringsFile = File(rootDir, "values-ru/strings.xml")
        val ukStringsFile = File(rootDir, "values-uk/strings.xml")
        val zhStringsFile = File(rootDir, "values-zh-rCN/strings.xml")
        val deStringsFile = File(rootDir, "values-de/strings.xml")

        assertTrue("Default strings file must exist", defaultStringsFile.exists())
        assertTrue("Russian strings file must exist", ruStringsFile.exists())
        assertTrue("Ukrainian strings file must exist", ukStringsFile.exists())
        assertTrue("Chinese strings file must exist", zhStringsFile.exists())
        assertTrue("German strings file must exist", deStringsFile.exists())

        val defaultKeys = extractStringKeys(defaultStringsFile)
        assertTrue("Should have non-empty keys", defaultKeys.isNotEmpty())

        val locales = mapOf(
            "ru" to ruStringsFile,
            "uk" to ukStringsFile,
            "zh-CN" to zhStringsFile,
            "de" to deStringsFile
        )

        for ((locale, file) in locales) {
            val keys = extractStringKeys(file)
            val missing = defaultKeys - keys
            val extra = keys - defaultKeys
            assertEquals("Locale [$locale] has missing keys: $missing", emptySet<String>(), missing)
            assertEquals("Locale [$locale] has extra keys: $extra", emptySet<String>(), extra)
        }
    }

    private fun extractStringKeys(file: File): Set<String> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")
        val keys = mutableSetOf<String>()
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            val nameAttr = node.attributes.getNamedItem("name")?.nodeValue
            if (nameAttr != null) {
                keys.add(nameAttr)
            }
        }
        return keys
    }
}
