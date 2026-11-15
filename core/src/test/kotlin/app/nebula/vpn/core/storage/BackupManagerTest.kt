package app.nebula.vpn.core.storage

import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.AEADBadTagException

class BackupManagerTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val sampleProfile = ProxyProfile(
        id = "test-prof-1",
        name = "Fast VLESS Node",
        protocol = ProtocolType.VLESS,
        server = "1.2.3.4",
        port = 443,
        authSecret = "99999999-9999-9999-9999-999999999999"
    )

    private val sampleRule = RoutingRule(
        id = "rule-1",
        type = RuleType.IP_CIDR,
        value = "192.168.0.0/16",
        action = RuleAction.DIRECT
    )

    private val sampleSettings = DesktopSettings(
        languageCode = "en",
        mode = "TUN",
        mixedInboundPort = 2080
    )

    @Test
    fun testPlaintextRoundTrip() {
        val payload = NebulaBackupPayload(
            profiles = listOf(sampleProfile),
            rules = listOf(sampleRule),
            settings = sampleSettings
        )

        val exported = BackupManager.exportBackup(payload, password = null)
        assertTrue(exported.contains("\"encrypted\": false"))

        val imported = BackupManager.importBackup(exported, password = null)
        assertEquals(1, imported.profiles.size)
        assertEquals("Fast VLESS Node", imported.profiles[0].name)
        assertEquals(1, imported.rules.size)
        assertEquals("192.168.0.0/16", imported.rules[0].value)
        assertEquals(RuleAction.DIRECT, imported.rules[0].action)
        assertEquals("TUN", imported.settings?.mode)
    }

    @Test
    fun testEncryptedRoundTripWithPassword() {
        val payload = NebulaBackupPayload(
            profiles = listOf(sampleProfile),
            rules = listOf(sampleRule),
            settings = sampleSettings
        )

        val password = "StrongSecretPassword123!"
        val exported = BackupManager.exportBackup(payload, password = password)
        assertTrue(exported.contains("\"encrypted\": true"))
        assertTrue(exported.contains("\"PBKDF2WithHmacSHA256\""))

        // Correct password restores data
        val imported = BackupManager.importBackup(exported, password = password)
        assertEquals(1, imported.profiles.size)
        assertEquals("Fast VLESS Node", imported.profiles[0].name)

        // Wrong password throws AEADBadTagException
        try {
            BackupManager.importBackup(exported, password = "WrongPassword")
            fail("Should fail with bad password")
        } catch (e: Exception) {
            assertTrue(e is AEADBadTagException || e.cause is AEADBadTagException)
        }
    }

    @Test
    fun testMigrationBackup() {
        val baseDir = tempFolder.newFolder("nebula_data")
        File(baseDir, "profiles.json").writeText("{\"schemaVersion\":1}")
        File(baseDir, "rules.json").writeText("{\"schemaVersion\":1}")
        File(baseDir, "settings.json").writeText("{\"schemaVersion\":1}")

        val backupDir = BackupManager.createMigrationBackup(baseDir)
        assertNotNull(backupDir)
        assertTrue(backupDir!!.exists())
        assertTrue(File(backupDir, "profiles.json").exists())
        assertTrue(File(backupDir, "rules.json").exists())
        assertTrue(File(backupDir, "settings.json").exists())
    }
}
