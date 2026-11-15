package app.nebula.vpn.desktop

import app.nebula.vpn.desktop.i18n.AppLanguage
import app.nebula.vpn.desktop.i18n.DesktopStrings
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationCompletenessTest {

    @Test
    fun testAllLanguagesHaveIdenticalKeys() {
        val field = DesktopStrings::class.java.getDeclaredField("translations")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val translations = field.get(DesktopStrings) as Map<AppLanguage, Map<String, String>>

        val enKeys = translations[AppLanguage.EN]?.keys ?: emptySet()
        assertTrue("English keys should not be empty", enKeys.isNotEmpty())

        AppLanguage.entries.forEach { lang ->
            val langKeys = translations[lang]?.keys ?: emptySet()
            val missingInLang = enKeys - langKeys
            val extraInLang = langKeys - enKeys

            assertTrue(
                "Language ${lang.code} is missing keys: $missingInLang",
                missingInLang.isEmpty()
            )
            assertTrue(
                "Language ${lang.code} has unexpected extra keys: $extraInLang",
                extraInLang.isEmpty()
            )
        }
    }
}
