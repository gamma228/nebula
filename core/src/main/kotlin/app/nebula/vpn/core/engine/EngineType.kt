package app.nebula.vpn.core.engine

/**
 * Top-level Tunnel Engine selector in Nebula.
 */
enum class EngineType(
    val id: String,
    val titleKey: String,
    val descriptionKey: String
) {
    SING_BOX(
        id = "sing-box",
        titleKey = "engine_sing_box_title",
        descriptionKey = "engine_sing_box_desc"
    ),
    TOR(
        id = "tor",
        titleKey = "engine_tor_title",
        descriptionKey = "engine_tor_desc"
    );

    companion object {
        fun fromId(id: String?): EngineType {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SING_BOX
        }
    }
}
