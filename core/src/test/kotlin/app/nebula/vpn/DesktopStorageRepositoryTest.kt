package app.nebula.vpn

import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
import app.nebula.vpn.core.model.TlsConfig
import app.nebula.vpn.core.storage.DesktopStorageRepository
import app.nebula.vpn.core.storage.WindowsDpapiCipher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopStorageRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testDpapiCipherRoundTrip() {
        val originalSecret = "c0242d1b-db7a-4dcf-95ff-e10a3ef1c1a3"
        val cipherText = WindowsDpapiCipher.encrypt(originalSecret)
        assertTrue(cipherText.isNotEmpty())
        assertTrue("Must have encryption prefix", cipherText.startsWith("DPAPI:") || cipherText.startsWith("PLAIN:"))

        val decrypted = WindowsDpapiCipher.decrypt(cipherText)
        assertEquals("Decrypted secret must match original", originalSecret, decrypted)
    }

    @Test
    fun testDesktopStorageRepositoryRoundTrip() = runBlocking {
        val repo = DesktopStorageRepository(baseDir = tempFolder.root)

        val testProfile = ProxyProfile(
            id = "test-prof-1",
            name = "Swedish-Test",
            protocol = ProtocolType.VLESS,
            server = "87.242.127.115",
            port = 443,
            authSecret = "secret-uuid-12345",
            tls = TlsConfig(
                enabled = true,
                serverName = "lk.x5.ru",
                realityPublicKey = "pubkey-reality-test-key"
            )
        )

        repo.saveProfile(testProfile)
        repo.setSelectedProfileId("test-prof-1")

        val fetched = repo.getProfileById("test-prof-1")
        assertNotNull(fetched)
        assertEquals(testProfile.id, fetched!!.id)
        assertEquals(testProfile.name, fetched.name)
        assertEquals(testProfile.server, fetched.server)
        assertEquals(testProfile.port, fetched.port)
        assertEquals("Secret must be transparently decrypted", testProfile.authSecret, fetched.authSecret)
        assertEquals("Reality key must be transparently decrypted", testProfile.tls.realityPublicKey, fetched.tls.realityPublicKey)

        val selectedId = repo.getSelectedProfileId()
        assertEquals("test-prof-1", selectedId)

        // Test Rules
        val testRule = RoutingRule(
            id = "rule-1",
            type = RuleType.DOMAIN_SUFFIX,
            value = "example.org",
            action = RuleAction.PROXY,
            priority = 10
        )
        repo.saveRule(testRule)

        val rules = repo.getAllRules()
        assertEquals(1, rules.size)
        assertEquals("example.org", rules[0].value)

        // Test Settings
        val settings = repo.getSettings()
        assertEquals("ru", settings.languageCode)
        repo.updateSettings { it.copy(languageCode = "en", mode = "SYSTEM_PROXY") }
        val updatedSettings = repo.getSettings()
        assertEquals("en", updatedSettings.languageCode)
        assertEquals("SYSTEM_PROXY", updatedSettings.mode)
    }
}
