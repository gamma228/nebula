package app.nebula.vpn.core.parser

import java.util.UUID

object HwidManager {

    fun generateRandomHwid(): String = UUID.randomUUID().toString()

    fun buildDeviceHeaders(
        hwid: String,
        osVersion: String = "14",
        deviceModel: String = "Nebula Client"
    ): Map<String, String> {
        return mapOf(
            "X-HWID" to hwid,
            "X-Device-OS" to "Android",
            "X-Ver-OS" to osVersion,
            "X-Device-Model" to deviceModel
        )
    }
}
