package app.nebula.vpn.core.tor

import app.nebula.vpn.core.carrier.NetworkType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap

enum class BridgeSource(val displayName: String) {
    BUILT_IN("Built-in Default"),
    CUSTOM_POOL("Custom Bridge Pool")
}

enum class PlanFailureReason {
    BRIDGE_DEAD,
    TRANSPORT_BLOCKED,
    CARRIER_UNREACHABLE,
    CARRIER_OK_BRIDGE_DEAD,
    NO_NETWORK,
    WRONG_CLOCK,
    UNKNOWN;

    fun getSuggestedFix(): String {
        return when (this) {
            BRIDGE_DEAD -> "This bridge endpoint is not responding. Request fresh bridges from @GetBridgesBot or bridges.torproject.org."
            TRANSPORT_BLOCKED -> "This transport is blocked by ISP DPI. Try WebTunnel or enable Carrier Mode ('Tor via my server')."
            CARRIER_UNREACHABLE -> "The carrier server is unreachable. Check your subscription node or try another server."
            CARRIER_OK_BRIDGE_DEAD -> "Carrier server connected, but the Tor bridge behind it failed. Update your bridge lines."
            NO_NETWORK -> "No active network connection detected. Check your Wi-Fi or cellular connection."
            WRONG_CLOCK -> "Device clock skew detected. Tor requires accurate time; synchronize clock in Android Settings."
            UNKNOWN -> "Connection attempt timed out. Try the next transport in the plan."
        }
    }
}

data class PlanAttempt(
    val id: String,
    val transport: BridgeTransport,
    val bridgeSource: BridgeSource = BridgeSource.BUILT_IN,
    val useCarrier: Boolean = false,
    val timeoutMs: Long = 10000L,
    val isEnabled: Boolean = true,
    val isPinned: Boolean = false
) {
    val displayName: String
        get() {
            val carrierTag = if (useCarrier) " [via Carrier]" else " [Direct]"
            return "${transport.displayName} (${bridgeSource.displayName})$carrierTag"
        }
}

data class AttemptResult(
    val attempt: PlanAttempt,
    val isSuccess: Boolean,
    val latencyMs: Long = 0L,
    val failureReason: PlanFailureReason? = null,
    val rawError: String? = null,
    val nextStepSuggestion: String? = null
)

sealed class PlanProgress {
    data class AttemptStarting(
        val attemptIndex: Int,
        val totalAttempts: Int,
        val attempt: PlanAttempt
    ) : PlanProgress()

    data class AttemptFinished(
        val attemptIndex: Int,
        val totalAttempts: Int,
        val result: AttemptResult
    ) : PlanProgress()

    data class PlanCompleted(
        val workingAttempt: PlanAttempt?,
        val allResults: List<AttemptResult>
    ) : PlanProgress()

    data class PlanCancelled(
        val completedResults: List<AttemptResult>
    ) : PlanProgress()
}

/**
 * Tor Connection Plan Engine.
 *
 * Implements an ordered list of connection attempts:
 * (transport, bridge source, carrier yes/no) with automatic fallback and diagnostics.
 */
class ConnectionPlanEngine {

    private val lastWorkingByNetwork = ConcurrentHashMap<NetworkType, PlanAttempt>()
    private val attempts = mutableListOf<PlanAttempt>()

    init {
        resetToDefaultPlan()
    }

    @Synchronized
    fun resetToDefaultPlan() {
        attempts.clear()
        attempts.addAll(
            listOf(
                PlanAttempt("p_webtunnel_direct", BridgeTransport.WEBTUNNEL, BridgeSource.BUILT_IN, useCarrier = false),
                PlanAttempt("p_obfs4_direct", BridgeTransport.OBFS4, BridgeSource.BUILT_IN, useCarrier = false),
                PlanAttempt("p_snowflake_direct", BridgeTransport.SNOWFLAKE, BridgeSource.BUILT_IN, useCarrier = false),
                PlanAttempt("p_webtunnel_carrier", BridgeTransport.WEBTUNNEL, BridgeSource.BUILT_IN, useCarrier = true),
                PlanAttempt("p_obfs4_carrier", BridgeTransport.OBFS4, BridgeSource.BUILT_IN, useCarrier = true),
                PlanAttempt("p_custom_carrier", BridgeTransport.VANILLA, BridgeSource.CUSTOM_POOL, useCarrier = true),
                PlanAttempt("p_custom_direct", BridgeTransport.VANILLA, BridgeSource.CUSTOM_POOL, useCarrier = false)
            )
        )
    }

    @Synchronized
    fun getPlanAttempts(currentNetwork: NetworkType? = null): List<PlanAttempt> {
        val lastWorking = if (currentNetwork != null) lastWorkingByNetwork[currentNetwork] else null

        return attempts.sortedWith(
            compareByDescending<PlanAttempt> { it.isPinned }
                .thenByDescending { it.id == lastWorking?.id }
                .thenByDescending { it.isEnabled }
        )
    }

    @Synchronized
    fun setAttempts(newAttempts: List<PlanAttempt>) {
        attempts.clear()
        attempts.addAll(newAttempts)
    }

    @Synchronized
    fun pinAttempt(attemptId: String, pinned: Boolean) {
        val idx = attempts.indexOfFirst { it.id == attemptId }
        if (idx >= 0) {
            attempts[idx] = attempts[idx].copy(isPinned = pinned)
        }
    }

    @Synchronized
    fun toggleAttemptEnabled(attemptId: String, enabled: Boolean) {
        val idx = attempts.indexOfFirst { it.id == attemptId }
        if (idx >= 0) {
            attempts[idx] = attempts[idx].copy(isEnabled = enabled)
        }
    }

    fun recordWorkingAttempt(attempt: PlanAttempt, networkType: NetworkType) {
        lastWorkingByNetwork[networkType] = attempt
    }

    fun getLastWorkingAttempt(networkType: NetworkType): PlanAttempt? {
        return lastWorkingByNetwork[networkType]
    }

    /**
     * Executes the connection plan step by step with live status emissions.
     * Takes an [executor] lambda that attempts connecting to a single [PlanAttempt]
     * and returns an [AttemptResult].
     */
    fun executePlan(
        networkType: NetworkType,
        executor: suspend (PlanAttempt) -> AttemptResult
    ): Flow<PlanProgress> = flow {
        val orderedPlan = getPlanAttempts(networkType).filter { it.isEnabled }
        val results = mutableListOf<AttemptResult>()

        var workingAttempt: PlanAttempt? = null

        for ((index, attempt) in orderedPlan.withIndex()) {
            emit(PlanProgress.AttemptStarting(index + 1, orderedPlan.size, attempt))

            try {
                val result = executor(attempt)
                results.add(result)
                emit(PlanProgress.AttemptFinished(index + 1, orderedPlan.size, result))

                if (result.isSuccess) {
                    workingAttempt = attempt
                    recordWorkingAttempt(attempt, networkType)
                    break
                }
            } catch (e: CancellationException) {
                emit(PlanProgress.PlanCancelled(results))
                throw e
            } catch (e: Exception) {
                val failure = AttemptResult(
                    attempt = attempt,
                    isSuccess = false,
                    failureReason = PlanFailureReason.UNKNOWN,
                    rawError = e.message,
                    nextStepSuggestion = PlanFailureReason.UNKNOWN.getSuggestedFix()
                )
                results.add(failure)
                emit(PlanProgress.AttemptFinished(index + 1, orderedPlan.size, failure))
            }
        }

        emit(PlanProgress.PlanCompleted(workingAttempt, results))
    }
}
