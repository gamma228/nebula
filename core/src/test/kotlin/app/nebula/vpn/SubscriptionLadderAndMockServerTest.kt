package app.nebula.vpn

import app.nebula.vpn.core.model.Result
import app.nebula.vpn.core.parser.HwidManager
import app.nebula.vpn.core.parser.SubscriptionException
import app.nebula.vpn.core.parser.SubscriptionFetcher
import app.nebula.vpn.core.parser.UserAgentPreset
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.Base64
import java.util.zip.GZIPOutputStream

class SubscriptionLadderAndMockServerTest {

    private var server: HttpServer? = null
    private var serverPort: Int = 0

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        serverPort = server!!.address.port
        server!!.start()
    }

    @After
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun testFallbackLadderFindsWinningPresetWhenFirstFailsWithHtml() = runBlocking {
        // Handler returns HTML if UA is default Nebula, but returns valid sing-box JSON if UA is sing-box!
        server!!.createContext("/sub") { exchange ->
            val ua = exchange.requestHeaders.getFirst("User-Agent") ?: ""
            if (ua.contains("Nebula")) {
                val html = "<html><body><h1>Please use a supported client</h1></body></html>"
                val bytes = html.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "text/html")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
                exchange.close()
            } else if (ua.contains("sing-box")) {
                val json = """
                    {
                      "outbounds": [
                        {
                          "type": "vless",
                          "tag": "Working Node",
                          "server": "1.2.3.4",
                          "server_port": 443,
                          "uuid": "11111111-2222-3333-4444-555555555555"
                        }
                      ]
                    }
                """.trimIndent()
                val bytes = json.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
                exchange.close()
            } else {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
        }

        val url = "http://127.0.0.1:$serverPort/sub"
        val result = SubscriptionFetcher.fetchWithLadder(url, initialPreset = UserAgentPreset.NEBULA)

        assertTrue(result is Result.Success)
        val ladderResult = (result as Result.Success).data
        assertEquals(UserAgentPreset.SING_BOX, ladderResult.winningPreset)
        assertEquals(1, ladderResult.fetchResult.profiles.size)
        assertEquals("Working Node", ladderResult.fetchResult.profiles[0].name)
        assertEquals(2, ladderResult.attemptsCount) // 1st was Nebula (HTML), 2nd was sing-box (Success!)
    }

    @Test
    fun testDeviceHeadersSentAndVerifiedByMockPanel() = runBlocking {
        // Remnawave mock: checks for X-HWID and X-Device-OS
        server!!.createContext("/remnawave") { exchange ->
            val hwid = exchange.requestHeaders.getFirst("X-HWID")
            val os = exchange.requestHeaders.getFirst("X-Device-OS")

            if (hwid.isNullOrBlank() || os.isNullOrBlank()) {
                // Return HWID required signals and stub nodes
                val stubVless = "vless://00000000-0000-0000-0000-000000000000@0.0.0.0:1?encryption=none#%E2%9A%A0%EF%B8%8F%20HWID%20Required"
                val b64 = Base64.getEncoder().encodeToString(stubVless.toByteArray(Charsets.UTF_8))
                exchange.responseHeaders.add("x-hwid-active", "true")
                exchange.responseHeaders.add("x-hwid-not-supported", "true")
                val bytes = b64.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
                exchange.close()
            } else {
                // HWID present: return valid node
                val realVless = "vless://12345678-1234-1234-1234-123456789abc@real.example.com:443?security=none#Real%20Node"
                val b64 = Base64.getEncoder().encodeToString(realVless.toByteArray(Charsets.UTF_8))
                exchange.responseHeaders.add("x-hwid-active", "true")
                val bytes = b64.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
                exchange.close()
            }
        }

        val url = "http://127.0.0.1:$serverPort/remnawave"

        // 1. Fetch WITHOUT HWID headers -> stub response flagged with isHwidRequired=true
        val resultWithoutHwid = SubscriptionFetcher.fetch(url)
        assertTrue(resultWithoutHwid is Result.Success)
        val dataWithoutHwid = (resultWithoutHwid as Result.Success).data
        assertTrue(dataWithoutHwid.isHwidRequired)
        assertTrue(dataWithoutHwid.hasStubNodesOnly)

        // 2. Fetch WITH HWID headers -> real node returned!
        val testHwid = HwidManager.generateRandomHwid()
        val headers = HwidManager.buildDeviceHeaders(testHwid, "14", "Pixel 8")
        val resultWithHwid = SubscriptionFetcher.fetch(url, extraHeaders = headers)

        assertTrue(resultWithHwid is Result.Success)
        val dataWithHwid = (resultWithHwid as Result.Success).data
        assertFalse(dataWithHwid.hasStubNodesOnly)
        assertEquals(1, dataWithHwid.profiles.size)
        assertEquals("Real Node", dataWithHwid.profiles[0].name)
    }

    @Test
    fun testGzipCompressedResponseHandling() = runBlocking {
        server!!.createContext("/gzip") { exchange ->
            val content = "vless://a1b2c3d4-1234-1234-1234-1234567890ab@gzip.example.com:443?type=tcp#GzipNode"
            val b64 = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))

            val baos = ByteArrayOutputStream()
            GZIPOutputStream(baos).use { gz -> gz.write(b64.toByteArray(Charsets.UTF_8)) }
            val gzippedBytes = baos.toByteArray()

            exchange.responseHeaders.add("Content-Encoding", "gzip")
            exchange.responseHeaders.add("Content-Type", "text/plain")
            exchange.sendResponseHeaders(200, gzippedBytes.size.toLong())
            exchange.responseBody.write(gzippedBytes)
            exchange.close()
        }

        val url = "http://127.0.0.1:$serverPort/gzip"
        val result = SubscriptionFetcher.fetch(url)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(1, data.profiles.size)
        assertEquals("GzipNode", data.profiles[0].name)
    }

    @Test
    fun testRedirectChainFollowed() = runBlocking {
        server!!.createContext("/step1") { exchange ->
            exchange.responseHeaders.add("Location", "/step2")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server!!.createContext("/step2") { exchange ->
            val content = "trojan://pass@trojan.example.com:443#RedirectedTrojan"
            val b64 = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))
            val bytes = b64.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.write(bytes)
            exchange.close()
        }

        val url = "http://127.0.0.1:$serverPort/step1"
        val result = SubscriptionFetcher.fetch(url)
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(1, data.profiles.size)
        assertEquals("RedirectedTrojan", data.profiles[0].name)
    }

    @Test
    fun testHttpErrorClassification() = runBlocking {
        server!!.createContext("/err403") { exchange ->
            exchange.sendResponseHeaders(403, -1)
            exchange.close()
        }

        val url = "http://127.0.0.1:$serverPort/err403"
        val result = SubscriptionFetcher.fetch(url)
        assertTrue(result is Result.Error)
        val err = (result as Result.Error).exception
        assertTrue(err is SubscriptionException.HttpError)
        assertEquals(403, (err as SubscriptionException.HttpError).statusCode)
    }
}
