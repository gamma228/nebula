package app.nebula.vpn.core.tor

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class BridgeHealth(
    val bridge: ParsedBridge,
    val addedTimestamp: Long = System.currentTimeMillis(),
    var lastSuccessTimestamp: Long? = null,
    var failureCount: Int = 0,
    var isBlocked: Boolean = false
) {
    val isFresh: Boolean
        get() = (System.currentTimeMillis() - addedTimestamp) < 14L * 24 * 3600 * 1000 // 14 days
}

data class PoolHealthStatus(
    val totalValidBridges: Int,
    val bridgesByTransport: Map<BridgeTransport, Int>,
    val isSmall: Boolean,
    val isStale: Boolean,
    val warnings: List<String>
)

class BridgePoolManager {

    private val pool = ConcurrentHashMap<String, BridgeHealth>()
    private val rotationIndex = AtomicInteger(0)

    fun clear() {
        pool.clear()
    }

    /**
     * Adds or updates bridge lines from a multi-line string.
     * Returns the count of newly added valid bridges.
     */
    fun importBridges(multiLineText: String): Int {
        val parsedList = BridgeLineParser.parseLines(multiLineText)
        var addedCount = 0
        val now = System.currentTimeMillis()

        for (parsed in parsedList) {
            if (parsed.isValid) {
                val key = parsed.raw.trim()
                if (!pool.containsKey(key)) {
                    pool[key] = BridgeHealth(bridge = parsed, addedTimestamp = now)
                    addedCount++
                }
            }
        }
        return addedCount
    }

    fun getAllBridges(): List<BridgeHealth> {
        return pool.values.toList()
    }

    fun getValidBridges(transport: BridgeTransport? = null): List<ParsedBridge> {
        return pool.values
            .filter { it.bridge.isValid && (transport == null || it.bridge.transport == transport) }
            .map { it.bridge }
    }

    /**
     * Picks the next available bridge for the given transport, applying rotation and health filtering.
     * Deprioritizes bridges that have failed >= 3 consecutive times.
     */
    fun getNextBridge(transport: BridgeTransport? = null): ParsedBridge? {
        val candidates = pool.values
            .filter { it.bridge.isValid && (transport == null || it.bridge.transport == transport) }
            .sortedWith(compareBy({ it.failureCount >= 3 }, { it.failureCount }, { it.lastSuccessTimestamp ?: 0L }))

        if (candidates.isEmpty()) return null

        val unblocked = candidates.filter { !it.isBlocked }
        val eligible = if (unblocked.isNotEmpty()) unblocked else candidates
        val idx = (rotationIndex.getAndIncrement() and Int.MAX_VALUE) % eligible.size
        return eligible[idx].bridge
    }

    fun recordSuccess(bridge: ParsedBridge) {
        pool[bridge.raw.trim()]?.let {
            it.lastSuccessTimestamp = System.currentTimeMillis()
            it.failureCount = 0
            it.isBlocked = false
        }
    }

    fun recordFailure(bridge: ParsedBridge) {
        pool[bridge.raw.trim()]?.let {
            it.failureCount++
            if (it.failureCount >= 3) {
                it.isBlocked = true
            }
        }
    }

    /**
     * Inspects pool health to inform the user if their bridges are too few or too old (Russia DPI scrapes bridges every few weeks).
     */
    fun assessPoolHealth(): PoolHealthStatus {
        val valid = pool.values.filter { it.bridge.isValid }
        val totalValid = valid.size
        val byTransport = valid.groupBy { it.bridge.transport }
            .mapValues { it.value.size }

        val warnings = mutableListOf<String>()

        val isSmall = totalValid in 1..2
        if (totalValid == 0) {
            warnings.add("No custom bridges configured")
        } else if (isSmall) {
            warnings.add("Bridge pool is small ($totalValid). Consider adding 3+ bridges for resilience against DPI blocks.")
        }

        val now = System.currentTimeMillis()
        val staleCount = valid.count { (now - it.addedTimestamp) > 14L * 24 * 3600 * 1000 }
        val isStale = totalValid > 0 && staleCount == totalValid
        if (isStale) {
            warnings.add("Bridges are older than 14 days. WebTunnel and obfs4 bridges are frequently blocked by Russian ISPs; consider obtaining fresh bridges.")
        }

        return PoolHealthStatus(
            totalValidBridges = totalValid,
            bridgesByTransport = byTransport,
            isSmall = isSmall,
            isStale = isStale,
            warnings = warnings
        )
    }
}
