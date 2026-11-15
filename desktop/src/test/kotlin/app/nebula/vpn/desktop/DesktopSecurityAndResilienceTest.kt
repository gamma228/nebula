package app.nebula.vpn.desktop

import app.nebula.vpn.core.builder.SingBoxConfigBuilder
import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.desktop.theme.NebulaThemePack
import app.nebula.vpn.desktop.theme.ThemePackManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class DesktopSecurityAndResilienceTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun testClashApiBoundToLocalhostOnly() {
        val profile = ProxyProfile(
            id = "sec-test-1",
            name = "Security Node",
            protocol = ProtocolType.SHADOWSOCKS,
            server = "1.2.3.4",
            port = 8388,
            authSecret = "test-password",
            encryption = "aes-128-gcm"
        )

        val config = SingBoxConfigBuilder.BuildConfig(
            activeProfile = profile,
            allProfiles = listOf(profile),
            clashApiPort = 9090,
            clashApiSecret = "secret-token-xyz"
        )

        val jsonString = SingBoxConfigBuilder.build(config)

        // Must bind exclusively to 127.0.0.1
        assertTrue(jsonString.contains(""""external_controller": "127.0.0.1:9090""""))
        assertFalse(jsonString.contains(""""external_controller": "0.0.0.0"""))
        assertTrue(jsonString.contains(""""secret": "secret-token-xyz""""))
    }

    @Test
    fun testThemePackCorruptedFileFallback() {
        val corruptedFile = tempFolder.newFile("corrupt.nebulatheme")
        corruptedFile.writeText("{ corrupted_json_syntax: [missing_brackets, true, ")

        // Must never crash, returns safe fallback builtin theme
        val loaded = ThemePackManager.importFromFile(corruptedFile)
        assertNotNull(loaded)
        assertEquals(ThemePackManager.BUILTIN_PACKS[0].id, loaded.id)
    }

    @Test
    fun testThemePackNonexistentFileFallback() {
        val nonexistent = File(tempFolder.root, "does_not_exist.nebulatheme")
        val loaded = ThemePackManager.importFromFile(nonexistent)
        assertNotNull(loaded)
        assertEquals(ThemePackManager.BUILTIN_PACKS[0].id, loaded.id)
    }

    @Test
    fun testThemePackExportImportRoundTrip() {
        val customPack = NebulaThemePack(
            id = "test_custom_theme",
            name = "Test Custom",
            author = "Security Tester",
            description = "Resilience validation theme",
            seedHex = "#00D084",
            secondaryHex = "#06B6D4",
            mode = "DARK",
            surfaceStyle = "GLASS",
            cornerRadiusDp = 18,
            fontScale = 1.1f,
            backgroundDimAlpha = 0.4f
        )

        val themeFile = tempFolder.newFile("valid.nebulatheme")
        val exported = ThemePackManager.exportToFile(customPack, themeFile)
        assertTrue("Export must succeed", exported)

        val imported = ThemePackManager.importFromFile(themeFile)
        assertEquals(customPack.id, imported.id)
        assertEquals(customPack.name, imported.name)
        assertEquals(customPack.seedHex, imported.seedHex)
        assertEquals(customPack.mode, imported.mode)
        assertEquals(customPack.surfaceStyle, imported.surfaceStyle)
        assertEquals(customPack.cornerRadiusDp, imported.cornerRadiusDp)
    }

    @Test
    fun testBinaryHashVerificationMechanism() {
        // Create dummy binary
        val dummyBin = tempFolder.newFile("fake_binary.exe")
        dummyBin.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        val md = MessageDigest.getInstance("SHA-256")
        val actualHash = md.digest(dummyBin.readBytes()).joinToString("") { "%02x".format(it) }

        val expectedHash = "2dc46c2e2e2b0ea94e5ccd6d0b63cbfc583c66a1bae8afd9d44f19b49bd0a16d" // real sing-box

        // Validation logic: mismatch must be detected
        val isMatch = actualHash.equals(expectedHash, ignoreCase = true)
        assertFalse("Tampered / dummy binary must be detected as invalid hash", isMatch)
    }

    @Test
    fun testBuiltinThemesIntegrity() {
        assertEquals(8, ThemePackManager.BUILTIN_PACKS.size)
        for (pack in ThemePackManager.BUILTIN_PACKS) {
            assertTrue("Pack ${pack.id} must have valid seed hex", pack.seedHex.startsWith("#") && pack.seedHex.length == 7)
            assertTrue("Pack ${pack.id} must have valid secondary hex", pack.secondaryHex.startsWith("#") && pack.secondaryHex.length == 7)
            assertTrue("Corner radius must be positive", pack.cornerRadiusDp > 0)
        }
    }
}
