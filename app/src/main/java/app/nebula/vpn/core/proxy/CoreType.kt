package app.nebula.vpn.core.proxy

/**
 * Proxy engine types supported by Nebula.
 *
 * SING_BOX: Official sing-box v1.14.4 library (GPL-3.0) with native Cgo TUN fd handoff.
 * XRAY: Xray-core via AndroidLibXrayLite + hev-socks5-tunnel (MPL-2.0).
 */
enum class CoreType(val id: String, val displayName: String) {
    SING_BOX("sing-box", "sing-box (libbox v1.14.4)"),
    XRAY("xray", "Xray-core (v2rayNG stack)")
}
