package app.nebula.vpn.core.tor

import app.nebula.vpn.core.carrier.NetworkType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConnectionPlanEngineTest {

    private lateinit var planEngine: ConnectionPlanEngine

    @Before
    fun setup() {
        planEngine = ConnectionPlanEngine()
    }

    @Test
    fun testDefaultOrderingAndPinning() {
        val attempts = planEngine.getPlanAttempts()
        assertTrue("Default plan must contain 5+ attempts", attempts.size >= 5)

        // Pin obfs4 via carrier
        planEngine.pinAttempt("p_obfs4_carrier", pinned = true)

        val repinned = planEngine.getPlanAttempts()
        assertEquals("p_obfs4_carrier", repinned.first().id)
        assertTrue(repinned.first().isPinned)
    }

    @Test
    fun testNetworkAffinityOrdering() {
        val mobileAttempt = PlanAttempt("p_mobile_best", BridgeTransport.WEBTUNNEL, useCarrier = true)
        val wifiAttempt = PlanAttempt("p_wifi_best", BridgeTransport.OBFS4, useCarrier = false)

        planEngine.recordWorkingAttempt(mobileAttempt, NetworkType.MOBILE)
        planEngine.recordWorkingAttempt(wifiAttempt, NetworkType.WIFI)

        assertEquals("p_mobile_best", planEngine.getLastWorkingAttempt(NetworkType.MOBILE)?.id)
        assertEquals("p_wifi_best", planEngine.getLastWorkingAttempt(NetworkType.WIFI)?.id)
    }

    @Test
    fun testExecutePlanFallbackToCarrier() = runBlocking {
        val events = planEngine.executePlan(NetworkType.WIFI) { attempt ->
            if (attempt.id == "p_webtunnel_direct") {
                // Direct WebTunnel blocked by ISP
                AttemptResult(
                    attempt = attempt,
                    isSuccess = false,
                    failureReason = PlanFailureReason.TRANSPORT_BLOCKED,
                    nextStepSuggestion = PlanFailureReason.TRANSPORT_BLOCKED.getSuggestedFix()
                )
            } else if (attempt.id == "p_obfs4_direct") {
                // Direct obfs4 dead
                AttemptResult(
                    attempt = attempt,
                    isSuccess = false,
                    failureReason = PlanFailureReason.BRIDGE_DEAD,
                    nextStepSuggestion = PlanFailureReason.BRIDGE_DEAD.getSuggestedFix()
                )
            } else {
                // Success on third attempt (Snowflake or WebTunnel via carrier)
                AttemptResult(
                    attempt = attempt,
                    isSuccess = true,
                    latencyMs = 450L
                )
            }
        }.toList()

        val completed = events.last() as PlanProgress.PlanCompleted
        assertNotNull(completed.workingAttempt)
        assertEquals(3, completed.allResults.size)
        assertTrue(completed.allResults.last().isSuccess)
        assertEquals(completed.workingAttempt, planEngine.getLastWorkingAttempt(NetworkType.WIFI))
    }

    @Test
    fun testFailureReasonGuidance() {
        for (reason in PlanFailureReason.values()) {
            val fix = reason.getSuggestedFix()
            assertTrue("Fix suggestion for $reason must not be blank", fix.isNotBlank())
        }
    }
}
