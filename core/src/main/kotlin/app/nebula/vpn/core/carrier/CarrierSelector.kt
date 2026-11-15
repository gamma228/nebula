package app.nebula.vpn.core.carrier

import app.nebula.vpn.core.model.ProxyProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

enum class NetworkType {
    WIFI,
    MOBILE,
    OTHER
}

data class CarrierRankResult(
    val profile: ProxyProfile,
    val latencyMs: Long,
    val isSuccess: Boolean,
    val errorReason: String? = null
)

/**
 * Carrier Selector and Auto-Pick Engine for "Tor via my server".
 *
 * Requirements:
 * - Ranks and auto-picks working nodes from subscriptions or custom list.
 * - Prioritizes: 1. Pinned node, 2. Last working node on the current network type, 3. Lowest latency.
 * - Falls back to next available working node if a carrier node dies.
 * - Remembers last working carrier per network type (Wi-Fi / Mobile).
 */
class CarrierSelector(
    private val probeTimeoutMs: Long = 4000L,
    private val maxParallelProbes: Int = 4
) {

    private val lastWorkingCarrierByNetwork = ConcurrentHashMap<NetworkType, String>()
    private val carrierFailureCounts = ConcurrentHashMap<String, Int>()
    private var pinnedProfileId: String? = null

    fun pinProfile(profileId: String?) {
        pinnedProfileId = profileId
    }

    fun getPinnedProfileId(): String? = pinnedProfileId

    fun recordCarrierSuccess(profileId: String, networkType: NetworkType) {
        lastWorkingCarrierByNetwork[networkType] = profileId
        carrierFailureCounts[profileId] = 0
    }

    fun recordCarrierFailure(profileId: String) {
        carrierFailureCounts.compute(profileId) { _, current -> (current ?: 0) + 1 }
    }

    fun getLastWorkingCarrier(networkType: NetworkType): String? {
        return lastWorkingCarrierByNetwork[networkType]
    }

    /**
     * Ranks candidate profiles based on user pinning, network-type affinity, and failure count.
     */
    fun rankCandidates(
        candidates: List<ProxyProfile>,
        currentNetwork: NetworkType
    ): List<ProxyProfile> {
        val lastWorkingId = lastWorkingCarrierByNetwork[currentNetwork]

        return candidates
            .filter { (carrierFailureCounts[it.id] ?: 0) < 5 } // Exclude repeatedly failed carriers
            .sortedWith(
                compareByDescending<ProxyProfile> { it.id == pinnedProfileId }
                    .thenByDescending { it.id == lastWorkingId }
                    .thenBy { carrierFailureCounts[it.id] ?: 0 }
            )
    }

    /**
     * Concurrent probe with bounded parallelism.
     * Takes a test lambda [probeFunction] that returns latency or throws.
     */
    suspend fun probeAndSelectBest(
        candidates: List<ProxyProfile>,
        currentNetwork: NetworkType,
        probeFunction: suspend (ProxyProfile) -> Long
    ): ProxyProfile? = coroutineScope {
        if (candidates.isEmpty()) return@coroutineScope null

        // 1. If pinned candidate exists and has low failure, check it first
        pinnedProfileId?.let { pinnedId ->
            val pinned = candidates.find { it.id == pinnedId }
            if (pinned != null && (carrierFailureCounts[pinnedId] ?: 0) < 3) {
                try {
                    val lat = probeFunction(pinned)
                    if (lat > 0) {
                        recordCarrierSuccess(pinned.id, currentNetwork)
                        return@coroutineScope pinned
                    }
                } catch (_: Exception) {
                    recordCarrierFailure(pinned.id)
                }
            }
        }

        // 2. Rank candidates and test with bounded parallelism
        val orderedCandidates = rankCandidates(candidates, currentNetwork)
        val semaphore = Semaphore(maxParallelProbes)

        val deferredResults = orderedCandidates.map { profile ->
            async {
                semaphore.withPermit {
                    try {
                        val latency = probeFunction(profile)
                        CarrierRankResult(profile, latency, isSuccess = latency > 0)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        CarrierRankResult(profile, -1L, isSuccess = false, errorReason = e.message)
                    }
                }
            }
        }

        val results = deferredResults.awaitAll()
        val best = results
            .filter { it.isSuccess && it.latencyMs > 0 }
            .minByOrNull { it.latencyMs }

        if (best != null) {
            recordCarrierSuccess(best.profile.id, currentNetwork)
            best.profile
        } else {
            // Return first ranked candidate as fallback if probes failed or offline
            orderedCandidates.firstOrNull()
        }
    }
}
