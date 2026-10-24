package app.nebula.vpn.core.parser

/**
 * Parses package names from user input or mass-import text area.
 * Tolerates whitespace, line breaks, commas, semicolons, and '#' comments.
 */
object PackageNameParser {

    private val PACKAGE_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")

    fun parse(rawInput: String): List<String> {
        val result = mutableSetOf<String>()
        val lines = rawInput.lines()
        for (line in lines) {
            val cleanLine = line.substringBefore('#').trim()
            if (cleanLine.isEmpty()) continue

            // Split by comma, semicolon or whitespace
            val tokens = cleanLine.split(Regex("[,;\\s]+"))
            for (token in tokens) {
                val candidate = token.trim()
                if (candidate.isNotEmpty() && isValidPackageName(candidate)) {
                    result.add(candidate)
                }
            }
        }
        return result.sorted()
    }

    fun isValidPackageName(packageName: String): Boolean {
        return PACKAGE_REGEX.matches(packageName)
    }
}
