package app.nebula.vpn

import app.nebula.vpn.core.parser.PackageNameParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageNameParserTest {

    @Test
    fun testParseMultiLineAndComments() {
        val input = """
            # Browsers
            com.android.chrome
            org.mozilla.firefox, com.opera.browser; com.brave.browser # brave
            
            # Messengers
            org.telegram.messenger
            
            # Invalid lines
            not_a_package
            123.com.invalid
        """.trimIndent()

        val parsed = PackageNameParser.parse(input)
        val expected = listOf(
            "com.android.chrome",
            "com.brave.browser",
            "com.opera.browser",
            "org.mozilla.firefox",
            "org.telegram.messenger"
        ).sorted()

        assertEquals(expected, parsed)
    }

    @Test
    fun testIsValidPackageName() {
        assertTrue(PackageNameParser.isValidPackageName("app.nebula.vpn"))
        assertTrue(PackageNameParser.isValidPackageName("com.google.android.youtube"))
        assertFalse(PackageNameParser.isValidPackageName("invalid"))
        assertFalse(PackageNameParser.isValidPackageName("123.invalid"))
        assertFalse(PackageNameParser.isValidPackageName(""))
    }
}
