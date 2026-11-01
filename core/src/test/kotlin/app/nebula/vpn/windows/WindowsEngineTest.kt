package app.nebula.vpn.windows

import app.nebula.vpn.core.builder.SingBoxConfigBuilder
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.RoutingRule
import app.nebula.vpn.core.model.RuleAction
import app.nebula.vpn.core.model.RuleType
import app.nebula.vpn.core.windows.ProcessHelper
import app.nebula.vpn.core.windows.SingBoxBinaryVerifier
import app.nebula.vpn.core.windows.WindowsCoreController
import app.nebula.vpn.core.windows.WindowsSystemProxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WindowsEngineTest {

    private val singBoxExe = File("C:\\Users\\User\\tools\\sing-box.exe")
    private val wintunDll = File("C:\\Users\\User\\tools\\wintun.dll")

    @Test
    fun testBinaryHashVerificationPassesOnRealBinaries() {
        assertTrue("sing-box.exe must exist", singBoxExe.exists())
        assertTrue("sing-box.exe hash must match pinned SHA-256", SingBoxBinaryVerifier.verifySingBox(singBoxExe))

        assertTrue("wintun.dll must exist", wintunDll.exists())
        assertTrue("wintun.dll hash must match pinned SHA-256", SingBoxBinaryVerifier.verifyWintun(wintunDll))
    }

    @Test
    fun testBinaryHashVerificationFailsOnTamperedBinary() {
        val tempDummy = File.createTempFile("dummy_", ".exe")
        try {
            tempDummy.writeText("corrupted dummy binary payload")
            assertFalse("Tampered binary must fail hash verification", SingBoxBinaryVerifier.verifySingBox(tempDummy))
            assertFalse("Tampered dll must fail hash verification", SingBoxBinaryVerifier.verifyWintun(tempDummy))
        } finally {
            tempDummy.delete()
        }
    }

    @Test
    fun testSystemProxyRegistryLifecycleAndNotification() {
        val testDir = File.createTempFile("nebula_proxy_test_", "").apply { delete(); mkdirs() }
        val proxy = WindowsSystemProxy(testDir)
        val initialSettings = proxy.getCurrentSettings()

        try {
            // Enable proxy
            val enabled = proxy.enableProxy("127.0.0.1", 2189)
            assertTrue("Enabling proxy should return true", enabled)
            assertTrue("isProxyEnabled should be true", proxy.isProxyEnabled())

            val current = proxy.getCurrentSettings()
            assertEquals(1, current.proxyEnable)
            assertEquals("127.0.0.1:2189", current.proxyServer)

            // Disable proxy
            val disabled = proxy.disableProxy()
            assertTrue("Disabling proxy should return true", disabled)
            assertEquals(initialSettings.proxyEnable, proxy.getCurrentSettings().proxyEnable)
        } finally {
            proxy.disableProxy()
            testDir.deleteRecursively()
        }
    }

    @Test
    fun testStaleStateCleanupAfterCrashSimulation() {
        val testDir = File.createTempFile("nebula_stale_test_", "").apply { delete(); mkdirs() }
        val proxy = WindowsSystemProxy(testDir)

        try {
            // Simulate that proxy was enabled and app crashed leaving backup file
            proxy.enableProxy("127.0.0.1", 2199)
            assertTrue(proxy.isProxyEnabled())

            // Simulate next startup cleanup
            val cleaned = proxy.cleanupStaleState()
            assertTrue("Cleanup should succeed", cleaned)
            assertFalse("Proxy should be disabled after stale cleanup", proxy.isProxyEnabled())
        } finally {
            proxy.disableProxy()
            testDir.deleteRecursively()
        }
    }

    @Test
    fun testProcessHelperListsRunningApplications() {
        val processes = ProcessHelper.getRunningProcesses()
        assertNotNull(processes)
        assertTrue("Should detect running Windows desktop processes", processes.isNotEmpty())

        val first = processes.first()
        assertTrue("Process name should end with .exe", first.name.endsWith(".exe", ignoreCase = true))
        assertTrue("Process path should not be empty", first.path.isNotBlank())
    }

    @Test
    fun testSingBoxSystemProxyModeExecutionAndGracefulStop() = runBlocking {
        val testDir = File.createTempFile("nebula_run_test_", "").apply { delete(); mkdirs() }
        val proxy = WindowsSystemProxy(testDir)
        val controller = WindowsCoreController(
            singBoxBinary = singBoxExe,
            workingDir = testDir,
            systemProxy = proxy,
            clashApiPort = 9198
        )

        try {
            val profile = ProxyProfile(
                id = "test-node",
                name = "Test Direct Node",
                protocol = app.nebula.vpn.core.model.ProtocolType.VLESS,
                server = "127.0.0.1",
                port = 443,
                authSecret = "00000000-0000-0000-0000-000000000000"
            )
            val configJson = SingBoxConfigBuilder.build(
                SingBoxConfigBuilder.BuildConfig(
                    activeProfile = profile,
                    isTunEnabled = false,
                    localPortMixedInbound = 2198,
                    clashApiPort = 9198,
                    fakeDnsEnabled = false,
                    autoDetectInterface = false
                )
            )

            val startResult = controller.start(profile, configJson)
            assertTrue("Controller start should succeed: ${startResult.exceptionOrNull()?.message}", startResult.isSuccess)
            assertTrue(controller.connectionState.value is ConnectionState.Connected)
            assertTrue("Child process should be running", controller.isRunning())
            assertTrue("Windows system proxy should be active", proxy.isProxyEnabled())

            val stopResult = controller.stop()
            assertTrue("Controller stop should succeed", stopResult.isSuccess)
            assertEquals(ConnectionState.Disconnected, controller.connectionState.value)
            assertFalse("Child process should be terminated", controller.isRunning())
            assertFalse("Windows system proxy should be restored", proxy.isProxyEnabled())
        } finally {
            controller.stop()
            testDir.deleteRecursively()
        }
    }

    @Test
    fun testPerProcessSplitTunnelingConfigGenerationPassesSingBoxCheck() {
        val rules = listOf(
            RoutingRule(
                id = "rule-proc-1",
                type = RuleType.PROCESS_NAME,
                value = "chrome.exe",
                action = RuleAction.DIRECT,
                priority = 0
            ),
            RoutingRule(
                id = "rule-proc-2",
                type = RuleType.PROCESS_PATH,
                value = "C:\\Program Files\\ExampleApp\\app.exe",
                action = RuleAction.BLOCK,
                priority = 1
            )
        )

        val profile = ProxyProfile(
            id = "test-split",
            name = "Test Split Profile",
            protocol = app.nebula.vpn.core.model.ProtocolType.VLESS,
            server = "127.0.0.1",
            port = 443,
            authSecret = "00000000-0000-0000-0000-000000000000"
        )
        val configJson = SingBoxConfigBuilder.build(
            SingBoxConfigBuilder.BuildConfig(
                activeProfile = profile,
                customRules = rules,
                isTunEnabled = false,
                localPortMixedInbound = 2085,
                clashApiPort = 9095,
                autoDetectInterface = true
            )
        )

        assertTrue("config must contain process_name", configJson.contains("\"process_name\""))
        assertTrue("config must contain chrome.exe", configJson.contains("chrome.exe"))
        assertTrue("config must contain process_path", configJson.contains("\"process_path\""))
        assertTrue("config must contain ExampleApp", configJson.contains("ExampleApp"))

        // Run sing-box check on generated split config
        val tmp = File.createTempFile("split_cfg_", ".json")
        try {
            tmp.writeText(configJson)
            val proc = ProcessBuilder(singBoxExe.absolutePath, "check", "-c", tmp.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            val exitCode = proc.waitFor()
            assertEquals("sing-box check should exit 0 with process_name/process_path: $output", 0, exitCode)
        } finally {
            tmp.delete()
        }
    }
}
