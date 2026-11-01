package app.nebula.vpn.desktop

import app.nebula.vpn.core.model.ThemeMode
import app.nebula.vpn.desktop.i18n.AppLanguage
import app.nebula.vpn.desktop.i18n.DesktopStrings
import app.nebula.vpn.desktop.theme.DarkColorScheme
import app.nebula.vpn.desktop.theme.LightColorScheme
import app.nebula.vpn.desktop.theme.OledColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DesktopAppTest {

    @Test
    fun testLocalizationParityAcrossAllFiveLanguages() {
        val testKeys = listOf(
            "app_name", "nav_home", "nav_servers", "nav_routing", "nav_tools", "nav_settings",
            "btn_connect", "btn_disconnect", "mode_system_proxy", "mode_tun",
            "speed_up", "speed_down", "total_up", "total_down", "ping",
            "rules_title", "add_rule", "doctor_title", "settings_theme", "settings_language"
        )

        for (lang in AppLanguage.entries) {
            for (key in testKeys) {
                val value = DesktopStrings.get(key, lang)
                assertNotNull("Value for $key in ${lang.displayName} must not be null", value)
                assertFalse("Value for $key in ${lang.displayName} should not be default key", value == key)
                assertTrue("Value for $key in ${lang.displayName} should not be empty", value.isNotBlank())
            }
        }
    }

    @Test
    fun testDesktopThemeTokens() {
        // Dark theme background
        assertEquals(androidx.compose.ui.graphics.Color(0xFF0F172A), DarkColorScheme.background)
        // OLED theme background should be pitch black
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), OledColorScheme.background)
        // Light theme background
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF8FAFC), LightColorScheme.background)
    }

    @Test
    fun testSingleInstanceGuardSignaling() {
        val testLock = java.io.File.createTempFile("test_nebula_lock_", ".lock")
        val latch = CountDownLatch(1)

        val firstInstance = SingleInstanceGuard(lockFile = testLock) {
            latch.countDown()
        }

        try {
            val acquiredFirst = firstInstance.acquireOrSignal()
            assertTrue("First instance must acquire lock", acquiredFirst)

            // Second instance attempts to acquire on same lock file
            val secondInstance = SingleInstanceGuard(lockFile = testLock) {}
            val acquiredSecond = secondInstance.acquireOrSignal()
            assertFalse("Second instance must fail to acquire lock and signal first", acquiredSecond)

            val signaled = latch.await(3, TimeUnit.SECONDS)
            assertTrue("First instance must receive restore signal from second instance", signaled)
        } finally {
            firstInstance.release()
            testLock.delete()
        }
    }
}
