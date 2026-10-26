package app.nebula.vpn

import app.nebula.vpn.core.builder.SingBoxConfigBuilder
import app.nebula.vpn.core.model.Result
import app.nebula.vpn.core.parser.LinkParser
import app.nebula.vpn.core.parser.SubscriptionDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStreamReader

class SnapshotParityTest {

    private fun loadSnapshotText(): String {
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/black_ss_all_rus_20261026.txt")
            ?: error("Fixture not found")
        return InputStreamReader(stream, Charsets.UTF_8).readText()
    }

    @Test
    fun testParseSnapshotProfiles() {
        val raw = loadSnapshotText()
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        println("Total proxy link lines in snapshot: ${lines.size}")

        var successCount = 0
        var failureCount = 0
        val failures = mutableListOf<Pair<String, String>>()

        for (line in lines) {
            when (val parsed = LinkParser.parse(line)) {
                is Result.Success -> {
                    successCount++
                    val profile = parsed.data
                    println("SUCCESS [${profile.protocol}]: ${profile.name} -> ${profile.server}:${profile.port}")
                }
                is Result.Error -> {
                    failureCount++
                    failures.add(line to (parsed.message ?: parsed.exception.message ?: "Unknown error"))
                }
            }
        }

        println("=== PARSER RESULTS ===")
        println("Total: ${lines.size}")
        println("Success: $successCount")
        println("Failures: $failureCount")
        for ((line, error) in failures) {
            println("FAILURE: $error")
            println("  LINE: $line")
        }

        assertEquals("All 63 snapshot lines should be successfully parsed without failure", 0, failureCount)
        assertEquals(63, successCount)
    }

    @Test
    fun testValidateSingBoxConfigForEverySnapshotNode() {
        val raw = loadSnapshotText()
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val singBoxBin = File("C:/Users/User/tools/sing-box.exe")
        assertTrue("sing-box.exe must exist", singBoxBin.exists())

        val checkFailures = mutableListOf<String>()

        for ((index, line) in lines.withIndex()) {
            val parsed = LinkParser.parse(line)
            assertTrue("Must parse line $index", parsed is Result.Success)
            val profile = (parsed as Result.Success).data

            val configJson = SingBoxConfigBuilder.build(
                SingBoxConfigBuilder.BuildConfig(
                    activeProfile = profile
                )
            )

            val tempFile = File.createTempFile("singbox-test-node-$index-", ".json")
            try {
                tempFile.writeText(configJson)
                val process = ProcessBuilder(singBoxBin.absolutePath, "check", "-c", tempFile.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    println("CHECK FAILED for node $index (${profile.name}):\n$output")
                    checkFailures.add("Node $index (${profile.name}): $output")
                }
            } finally {
                tempFile.delete()
            }
        }

        if (checkFailures.isNotEmpty()) {
            println("=== SING-BOX CHECK FAILURES (${checkFailures.size}) ===")
            for (f in checkFailures) {
                println(f)
            }
        }

        assertEquals("All 63 generated sing-box configs must pass `sing-box check`", 0, checkFailures.size)
    }

    @Test
    fun testSubscriptionDecoderOnSnapshot() {
        val raw = loadSnapshotText()
        val result = SubscriptionDecoder.decode(raw)
        assertTrue("SubscriptionDecoder must succeed on raw snapshot: ${(result as? Result.Error)?.message}", result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(63, data.profiles.size)
        assertTrue("Title must be extracted from # profile-title", data.title?.contains("BLACK LISTS") == true)
    }
}
