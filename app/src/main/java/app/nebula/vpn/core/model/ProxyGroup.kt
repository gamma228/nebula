package app.nebula.vpn.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class GroupType {
    SELECTOR,
    URL_TEST,
    FALLBACK,
    LOAD_BALANCE
}

@Serializable
data class ProxyGroup(
    val id: String,
    val name: String,
    val type: GroupType = GroupType.SELECTOR,
    val profileIds: List<String> = emptyList(),
    val selectedProfileId: String? = null,
    val testUrl: String = "https://www.gstatic.com/generate_204",
    val testIntervalSeconds: Int = 300,
    val toleranceMs: Int = 50
)
