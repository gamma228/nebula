package app.nebula.vpn

import app.nebula.vpn.core.proxy.CoreFactory
import app.nebula.vpn.core.proxy.CoreType
import app.nebula.vpn.core.proxy.TunProvider
import app.nebula.vpn.core.proxy.XrayCore
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Milestone M1 Acceptance Tests:
 * - Section D: Static guards (no Thread.sleep in production, no GlobalScope, no delay(0))
 * - Section E1: ProxyCore abstraction with TWO engines (sing-box integrated, Xray-core documented stub)
 * - Section E1: App refuses to open tunnel without working engine (never blackholes traffic)
 */
class MilestoneM1Test {

    @Test
    fun `static guard - zero Thread sleep in production code`() {
        val mainSrcDir = File("src/main/java")
        val ktFiles = mainSrcDir.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("Source files should exist", ktFiles.isNotEmpty())

        val violations = mutableListOf<String>()
        ktFiles.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                // Exclude comments
                val trimmed = line.trim()
                if (!trimmed.startsWith("//") && !trimmed.startsWith("*")) {
                    if (line.contains("Thread.sleep")) {
                        violations.add("${file.name}:${index + 1} - $line")
                    }
                }
            }
        }
        assertTrue(
            "Found Thread.sleep in production code:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `static guard - zero GlobalScope in production code`() {
        val mainSrcDir = File("src/main/java")
        val ktFiles = mainSrcDir.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("Source files should exist", ktFiles.isNotEmpty())

        val violations = mutableListOf<String>()
        ktFiles.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("//") && !trimmed.startsWith("*")) {
                    if (line.contains("GlobalScope")) {
                        violations.add("${file.name}:${index + 1} - $line")
                    }
                }
            }
        }
        assertTrue(
            "Found GlobalScope in production code:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `static guard - zero delay(0) in production code`() {
        val mainSrcDir = File("src/main/java")
        val ktFiles = mainSrcDir.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("Source files should exist", ktFiles.isNotEmpty())

        val violations = mutableListOf<String>()
        ktFiles.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("//") && !trimmed.startsWith("*")) {
                    if (line.contains("delay(0)") || line.contains("delay(0L)")) {
                        violations.add("${file.name}:${index + 1} - $line")
                    }
                }
            }
        }
        assertTrue(
            "Found delay(0) in production code:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `proxy core abstraction supports two engine types`() {
        val values = CoreType.values()
        assertEquals(2, values.size)
        assertTrue(values.contains(CoreType.SING_BOX))
        assertTrue(values.contains(CoreType.XRAY))
    }

    @Test
    fun `xray core refuses to open tunnel when native engine unbundled`() = runTest {
        val xray = XrayCore()
        val mockTunProvider = mockk<TunProvider>(relaxed = true)

        try {
            xray.start("{}", mockTunProvider)
            fail("XrayCore must refuse to open tunnel without native library bundled")
        } catch (e: IllegalStateException) {
            assertTrue(
                "Exception must cite REAL ENGINE NOT INTEGRATED",
                e.message?.contains("REAL ENGINE NOT INTEGRATED") == true
            )
        }
    }
}
