package app.nebula.vpn

import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.proxy.BatchTestProgress
import app.nebula.vpn.core.proxy.DefaultLatencyTester
import app.nebula.vpn.core.proxy.LatencyProbe
import app.nebula.vpn.core.proxy.ProbeMethod
import app.nebula.vpn.core.proxy.ServerTestState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
class LatencyTesterTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private class FakeProbe(
        private val delayMsMap: Map<String, Long> = emptyMap(),
        private val shouldHangMap: Map<String, Boolean> = emptyMap(),
        private val shouldFailMap: Map<String, Boolean> = emptyMap()
    ) : LatencyProbe {
        override suspend fun probe(profile: ProxyProfile, method: ProbeMethod, timeoutMs: Long): Long {
            if (shouldHangMap[profile.id] == true) {
                delay(timeoutMs + 10_000)
                return -1
            }
            if (shouldFailMap[profile.id] == true) {
                throw RuntimeException("Network unreachable")
            }
            val d = delayMsMap[profile.id] ?: 50L
            delay(d)
            return d
        }
    }

    private fun createProfile(id: String): ProxyProfile {
        return ProxyProfile(
            id = id,
            name = "Node-$id",
            protocol = ProtocolType.VLESS,
            server = "1.2.3.4",
            port = 443,
            authSecret = "test"
        )
    }

    @Test
    fun `hung servers trigger timeout without stalling queue`() = testScope.runTest {
        val profiles = (1..10).map { createProfile("node-$it") }
        // node-1 and node-2 hang indefinitely; others respond in 50ms
        val fakeProbe = FakeProbe(
            shouldHangMap = mapOf("node-1" to true, "node-2" to true)
        )

        val tester = DefaultLatencyTester(
            probe = fakeProbe,
            dispatcher = testDispatcher,
            parallelism = 6,
            perServerTimeoutMs = 5000L,
            globalTimeoutMs = 15000L
        )

        val job = launch {
            tester.testBatch(profiles, ProbeMethod.TCP_HANDSHAKE)
        }

        // Advance past 5s per-server timeout
        advanceTimeBy(5100)
        advanceUntilIdle()

        val progress = tester.progress.value
        assertEquals(10, progress.completed)
        assertFalse(progress.isRunning)

        // node-1 and node-2 must be Timeout, rest must be Ok
        assertEquals(ServerTestState.Timeout, progress.currentServerStates["node-1"])
        assertEquals(ServerTestState.Timeout, progress.currentServerStates["node-2"])
        assertTrue(progress.currentServerStates["node-3"] is ServerTestState.Ok)
        assertTrue(progress.currentServerStates["node-10"] is ServerTestState.Ok)
    }

    @Test
    fun `cancel aborts in-flight batch and leaves no server in Testing state`() = testScope.runTest {
        val profiles = (1..20).map { createProfile("node-$it") }
        val fakeProbe = FakeProbe(
            delayMsMap = (1..20).associate { "node-$it" to 2000L }
        )

        val tester = DefaultLatencyTester(
            probe = fakeProbe,
            dispatcher = testDispatcher,
            parallelism = 6,
            perServerTimeoutMs = 5000L,
            globalTimeoutMs = 30000L
        )

        val job = launch {
            tester.testBatch(profiles, ProbeMethod.TCP_HANDSHAKE)
        }

        advanceTimeBy(500)
        assertTrue(tester.progress.value.isRunning)

        tester.cancel()
        advanceUntilIdle()

        val progress = tester.progress.value
        assertFalse(progress.isRunning)
        // No server should be left in Testing
        val anyTesting = progress.currentServerStates.values.any { it is ServerTestState.Testing }
        assertFalse("No server should be stuck in Testing state", anyTesting)
    }
}
