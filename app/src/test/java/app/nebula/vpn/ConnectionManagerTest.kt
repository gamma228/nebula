package app.nebula.vpn

import app.nebula.vpn.core.connection.ConnectionManager
import app.nebula.vpn.core.connection.DefaultConnectionManager
import app.nebula.vpn.core.connection.VpnServiceController
import app.nebula.vpn.core.model.ConnectionState
import app.nebula.vpn.core.model.TrafficStats
import app.nebula.vpn.core.proxy.ProxyCore
import app.nebula.vpn.core.proxy.TunProvider
import app.nebula.vpn.data.repository.ProfileRepository
import app.nebula.vpn.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val profileRepository: ProfileRepository = mockk(relaxed = true)
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val proxyCore: ProxyCore = mockk(relaxed = true)
    private val serviceController: VpnServiceController = mockk(relaxed = true)

    private lateinit var connectionManager: DefaultConnectionManager

    @Before
    fun setup() {
        connectionManager = DefaultConnectionManager(
            profileRepository = profileRepository,
            settingsRepository = settingsRepository,
            proxyCore = proxyCore,
            serviceController = serviceController,
            dispatcher = testDispatcher,
            scope = testScope
        )
    }

    @Test
    fun `initial state is Disconnected`() = testScope.runTest {
        assertEquals(ConnectionState.Disconnected, connectionManager.state.value)
    }

    @Test
    fun `disconnect while Connecting cancels in-flight job and finishes in Disconnected`() = testScope.runTest {
        // Mock a slow connection process
        coEvery { serviceController.startVpnService(any()) } coAnswers {
            delay(5000)
        }

        connectionManager.connect("profile-1")
        advanceTimeBy(500)
        assertEquals(ConnectionState.Connecting, connectionManager.state.value)

        // Disconnect immediately while connecting
        connectionManager.disconnect()
        advanceUntilIdle()

        assertEquals(ConnectionState.Disconnected, connectionManager.state.value)
        assertTrue(connectionManager.isUserRequestedDisconnect)
    }

    @Test
    fun `disconnect has 3 second timeout and forces termination if service hangs`() = testScope.runTest {
        // Mock service stop that hangs forever
        coEvery { serviceController.stopVpnService(any()) } coAnswers {
            delay(10000)
        }

        // Set to connected
        connectionManager.onServiceConnected("Test Node")
        assertEquals("Test Node", (connectionManager.state.value as ConnectionState.Connected).profileName)

        connectionManager.disconnect()
        advanceTimeBy(3100)
        advanceUntilIdle()

        assertEquals(ConnectionState.Disconnected, connectionManager.state.value)
        assertTrue(connectionManager.isUserRequestedDisconnect)
    }

    @Test
    fun `rapid 100 connect-disconnect cycles do not deadlock`() = testScope.runTest {
        repeat(100) { i ->
            connectionManager.connect("profile-$i")
            advanceTimeBy(10)
            connectionManager.disconnect()
            advanceTimeBy(10)
        }
        advanceUntilIdle()
        assertEquals(ConnectionState.Disconnected, connectionManager.state.value)
    }
}
