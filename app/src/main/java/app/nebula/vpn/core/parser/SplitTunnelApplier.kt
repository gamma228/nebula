package app.nebula.vpn.core.parser

import android.content.pm.PackageManager
import android.net.VpnService
import app.nebula.vpn.core.model.AppRoutingMode

/**
 * Applies split-tunneling package rules to [VpnService.Builder].
 *
 * Rules:
 * - Whitelist mode (FORWARD_APPS): calls ONLY [VpnService.Builder.addAllowedApplication].
 * - Blacklist mode (BYPASS_APPS): calls ONLY [VpnService.Builder.addDisallowedApplication].
 * - Allowed and Disallowed applications MUST NEVER be mixed on the same Builder.
 * - An empty whitelist NEVER tunnels all apps — it safely falls back to only allowing own app package.
 * - [PackageManager.NameNotFoundException] is caught per package and skipped safely.
 */
object SplitTunnelApplier {

    data class ApplyResult(
        val appliedPackages: List<String>,
        val missingPackages: List<String>
    )

    fun apply(
        builder: VpnService.Builder,
        packageManager: PackageManager,
        ownPackageName: String,
        mode: AppRoutingMode,
        targetPackages: Collection<String>
    ): ApplyResult {
        val applied = mutableListOf<String>()
        val missing = mutableListOf<String>()

        when (mode) {
            AppRoutingMode.GLOBAL -> {
                // Disallow own package to eliminate kernel loopback for app's internal probes/traffic
                try {
                    packageManager.getPackageInfo(ownPackageName, 0)
                    builder.addDisallowedApplication(ownPackageName)
                    applied.add(ownPackageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    missing.add(ownPackageName)
                }
            }
            AppRoutingMode.FORWARD_APPS -> {
                // Whitelist: ONLY addAllowedApplication
                val packagesToAllow = if (targetPackages.isEmpty()) {
                    // Safety: empty whitelist falls back to own package, never all apps
                    listOf(ownPackageName)
                } else {
                    targetPackages.toList()
                }

                for (pkg in packagesToAllow) {
                    try {
                        packageManager.getPackageInfo(pkg, 0)
                        builder.addAllowedApplication(pkg)
                        applied.add(pkg)
                    } catch (e: PackageManager.NameNotFoundException) {
                        missing.add(pkg)
                    }
                }
            }
            AppRoutingMode.BYPASS_APPS -> {
                // Blacklist: ONLY addDisallowedApplication (including own package)
                val packagesToDisallow = (targetPackages + ownPackageName).distinct()
                for (pkg in packagesToDisallow) {
                    try {
                        packageManager.getPackageInfo(pkg, 0)
                        builder.addDisallowedApplication(pkg)
                        applied.add(pkg)
                    } catch (e: PackageManager.NameNotFoundException) {
                        missing.add(pkg)
                    }
                }
            }
        }

        return ApplyResult(appliedPackages = applied, missingPackages = missing)
    }
}
