package app.nebula.vpn.core.parser

import app.nebula.vpn.core.model.Result
import java.io.File

object SubscriptionInspectorCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val urlFilePath = if (args.isNotEmpty()) args[0] else "tools/local/sub_url.txt"
        val userAgent = if (args.size > 1) args[1] else "Nebula/1.0.0 (Android; sing-box/1.14.4)"

        val urlFile = File(urlFilePath)
        if (!urlFile.exists()) {
            System.err.println("Error: URL file not found: $urlFilePath")
            System.err.println("Please create the file with the subscription URL (never committed).")
            System.exit(1)
        }

        val url = urlFile.readText().trim()
        if (url.isBlank()) {
            System.err.println("Error: URL file is empty.")
            System.exit(1)
        }

        val safeUaName = userAgent.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val cacheDir = File("tools/local/cached_responses")
        cacheDir.mkdirs()
        val cacheFile = File(cacheDir, "$safeUaName.txt")

        println("Running Subscription Inspector...")
        println("User-Agent: $userAgent")
        println("URL: [CONFIDENTIAL - MASKED]")

        when (val res = SubscriptionInspector.inspect(url, userAgent)) {
            is Result.Success -> {
                println(res.data.formatReport())
                // Cache raw response locally for offline analysis (git-ignored)
                cacheFile.writeText(res.data.rawBody)
                println("Cached response locally to: ${cacheFile.path} (${res.data.bodySizeBytes} bytes)")
            }
            is Result.Error -> {
                System.err.println("Inspection Failed: ${res.exception.message}")
                res.exception.printStackTrace()
                System.exit(2)
            }
        }
    }
}
