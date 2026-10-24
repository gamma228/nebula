package app.nebula.vpn

import app.nebula.vpn.core.model.ProtocolType
import app.nebula.vpn.core.model.Result
import app.nebula.vpn.core.parser.SubscriptionDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SubscriptionDecoderTest {

    @Test
    fun testDecodeBase64SubscriptionWithHeaders() {
        val links = """
            trojan://pass1@trojan1.com:443?sni=trojan1.com#Node-1
            trojan://pass2@trojan2.com:443?sni=trojan2.com#Node-2
        """.trimIndent()
        val b64 = Base64.getEncoder().encodeToString(links.toByteArray(Charsets.UTF_8))

        val headers = mapOf(
            "profile-title" to "My Premium VPN",
            "subscription-userinfo" to "upload=1024; download=2048; total=10737418240; expire=1735689600"
        )

        val result = SubscriptionDecoder.decode(b64, headers)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data

        assertEquals("My Premium VPN", data.title)
        assertEquals(1024L, data.uploadBytes)
        assertEquals(2048L, data.downloadBytes)
        assertEquals(10737418240L, data.totalBytes)
        assertEquals(1735689600L, data.expireEpochSeconds)

        assertEquals(2, data.profiles.size)
        assertEquals("Node-1", data.profiles[0].name)
        assertEquals(ProtocolType.TROJAN, data.profiles[0].protocol)
        assertEquals("Node-2", data.profiles[1].name)
    }
}
