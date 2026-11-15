package app.nebula.vpn.core.parser

enum class UserAgentPreset(val displayName: String, val headerValue: String) {
    NEBULA("Nebula (Default)", "Nebula/1.2.1 (Android; sing-box/1.14.4)"),
    SING_BOX("sing-box", "sing-box/1.14.4"),
    CLASH_META("Clash / Mihomo", "ClashMeta/1.18.0"),
    V2RAY_NG("v2rayNG", "v2rayNG/1.8.5"),
    CUSTOM("Custom", "");

    companion object {
        fun fromName(name: String?): UserAgentPreset {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NEBULA
        }
    }
}
