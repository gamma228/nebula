package app.nebula.vpn.core.parser

import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.ProxyProfile
import app.nebula.vpn.core.model.Result
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

object Sip008ShadowsocksParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class ParseResult(
        val profiles: List<ProxyProfile>,
        val totalCount: Int,
        val skippedCount: Int,
        val skippedReasons: List<Pair<String, String>>
    )

    fun parse(content: String): Result<ParseResult> {
        return try {
            val rootElement = json.parseToJsonElement(content.trim())
            val serversArray: JsonArray = when (rootElement) {
                is JsonObject -> rootElement["servers"]?.jsonArray
                    ?: return Result.Error(IllegalArgumentException("SIP008 JSON missing 'servers' array"))
                is JsonArray -> rootElement
                else -> return Result.Error(IllegalArgumentException("Invalid JSON root: expected object or array"))
            }

            val profiles = mutableListOf<ProxyProfile>()
            val skipped = mutableListOf<Pair<String, String>>()
            var total = 0

            for (elem in serversArray) {
                val obj = elem as? JsonObject ?: continue
                total++

                val server = obj["server"]?.jsonPrimitive?.content ?: ""
                val port = obj["server_port"]?.jsonPrimitive?.intOrNull ?: 8388
                val password = obj["password"]?.jsonPrimitive?.content ?: ""
                val method = obj["method"]?.jsonPrimitive?.content ?: "aes-128-gcm"
                val remarks = obj["remarks"]?.jsonPrimitive?.content
                    ?: obj["tag"]?.jsonPrimitive?.content
                    ?: "SS-$server:$port"

                if (server.isBlank() || password.isBlank()) {
                    skipped.add(remarks to "Missing server address or password")
                    continue
                }

                profiles.add(
                    ProxyProfile(
                        id = UUID.randomUUID().toString(),
                        name = remarks,
                        protocol = ProtocolType.SHADOWSOCKS,
                        server = server,
                        port = port,
                        authSecret = "$method:$password",
                        encryption = method
                    )
                )
            }

            Result.Success(
                ParseResult(
                    profiles = profiles,
                    totalCount = total,
                    skippedCount = skipped.size,
                    skippedReasons = skipped
                )
            )
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
