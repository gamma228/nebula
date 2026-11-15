package app.nebula.vpn

import app.nebula.vpn.core.parser.SubscriptionException
import app.nebula.vpn.core.parser.SubscriptionInspector
import app.nebula.vpn.ui.servers.SubscriptionErrorFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.xml.parsers.DocumentBuilderFactory

class SubscriptionErrorFormatterTest {

    @Test
    fun testAllExceptionsMapToDistinctErrorResIds() {
        assertEquals(R.string.sub_err_network_timeout, SubscriptionErrorFormatter.getErrorResId(SubscriptionException.NetworkTimeout(SocketTimeoutException("timeout"))))
        assertEquals(R.string.sub_err_network_timeout, SubscriptionErrorFormatter.getErrorResId(SocketTimeoutException("timeout")))
        assertEquals(R.string.sub_err_network_timeout, SubscriptionErrorFormatter.getErrorResId(UnknownHostException("dns fail")))

        assertEquals(R.string.sub_err_auth, SubscriptionErrorFormatter.getErrorResId(SubscriptionException.HttpError(401, "Unauthorized")))
        assertEquals(R.string.sub_err_auth, SubscriptionErrorFormatter.getErrorResId(SubscriptionException.HttpError(403, "Forbidden")))
        assertEquals(R.string.sub_err_not_found, SubscriptionErrorFormatter.getErrorResId(SubscriptionException.HttpError(404, "Not Found")))
        assertEquals(R.string.sub_err_rate_limited, SubscriptionErrorFormatter.getErrorResId(SubscriptionException.HttpError(429, "Too Many Requests")))
        assertEquals(R.string.sub_err_server_error, SubscriptionErrorFormatter.getErrorResId(SubscriptionException.HttpError(502, "Bad Gateway")))
        assertEquals(R.string.sub_err_http_generic, SubscriptionErrorFormatter.getErrorResId(SubscriptionException.HttpError(418, "I'm a teapot")))

        assertEquals(R.string.sub_err_html_web_page, SubscriptionErrorFormatter.getErrorResId(SubscriptionException.HtmlWebPage("<html/>")))
        assertEquals(R.string.sub_err_device_limit, SubscriptionErrorFormatter.getErrorResId(SubscriptionException.DeviceLimitReached("slot taken")))
        assertEquals(R.string.sub_err_hwid_required, SubscriptionErrorFormatter.getErrorResId(SubscriptionException.HwidRequired("need header")))
        assertEquals(R.string.sub_err_payload_too_large, SubscriptionErrorFormatter.getErrorResId(SubscriptionException.PayloadTooLarge(25_000_000L)))
        assertEquals(R.string.sub_err_empty, SubscriptionErrorFormatter.getErrorResId(SubscriptionException.EmptySubscription()))
        assertEquals(R.string.sub_err_unsupported_format, SubscriptionErrorFormatter.getErrorResId(SubscriptionException.UnsupportedFormat("random_binary")))
        assertEquals(R.string.sub_err_generic, SubscriptionErrorFormatter.getErrorResId(RuntimeException("unknown failure")))
    }

    @Test
    fun testAllSubscriptionStringsExistInAllFiveLocales() {
        val requiredKeys = listOf(
            "sub_err_network_timeout",
            "sub_err_auth",
            "sub_err_not_found",
            "sub_err_rate_limited",
            "sub_err_server_error",
            "sub_err_http_generic",
            "sub_err_html_web_page",
            "sub_err_device_limit",
            "sub_err_hwid_required",
            "sub_err_payload_too_large",
            "sub_err_empty",
            "sub_err_unsupported_format",
            "sub_err_generic",
            "sub_imported_with_skips",
            "sub_copy_debug_info",
            "sub_debug_info_copied",
            "sub_ua_preset_label",
            "sub_ua_nebula",
            "sub_ua_singbox",
            "sub_ua_clash",
            "sub_ua_v2rayng",
            "sub_ua_custom",
            "sub_ua_custom_hint",
            "sub_send_device_headers",
            "sub_send_device_headers_desc",
            "sub_hwid_regenerate",
            "sub_hwid_current",
            "sub_device_limit_dialog_title",
            "sub_device_limit_dialog_desc",
            "sub_action_enable_device_id",
            "sub_action_manage_devices",
            "sub_settings_title",
            "sub_notice_warning",
            "sub_traffic_unlimited",
            "sub_expire_permanent"
        )

        val locales = listOf(
            "en" to File("src/main/res/values/strings.xml"),
            "ru" to File("src/main/res/values-ru/strings.xml"),
            "uk" to File("src/main/res/values-uk/strings.xml"),
            "zh-CN" to File("src/main/res/values-zh-rCN/strings.xml"),
            "de" to File("src/main/res/values-de/strings.xml")
        )

        for ((locale, file) in locales) {
            assertTrue("Locale $locale strings file must exist", file.exists())
            val stringMap = extractStrings(file)
            for (key in requiredKeys) {
                assertTrue("Locale [$locale] must define string [$key]", !stringMap[key].isNullOrBlank())
            }
        }
    }

    @Test
    fun testSanitizedDebugInfoMasking() {
        val rawSensitive = """
            vless://12345678-1234-1234-1234-123456789abc@198.51.100.42:443?security=reality&pbk=AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_#Node1
            https://vpn.example.com/sub/secret_token_abc123xyz789
            X-HWID: 98765432-4321-4321-4321-0987654321ba
        """.trimIndent()

        val masked = SubscriptionInspector.maskSensitiveData(rawSensitive)
        assertFalse("Must not leak UUID", masked.contains("12345678-1234-1234-1234-123456789abc"))
        assertFalse("Must not leak IP address", masked.contains("198.51.100.42"))
        assertFalse("Must not leak public key", masked.contains("AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_"))
        assertFalse("Must not leak secret token", masked.contains("secret_token_abc123xyz789"))
        assertTrue("Must contain masked markers", masked.contains("[UUID]") && masked.contains("[IPV4]") && masked.contains("pbk=****"))
    }

    private fun extractStrings(file: File): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")
        val map = mutableMapOf<String, String>()
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            val name = node.attributes.getNamedItem("name")?.nodeValue
            if (name != null) {
                map[name] = node.textContent
            }
        }
        return map
    }
}
