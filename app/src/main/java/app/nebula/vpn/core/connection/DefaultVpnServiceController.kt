package app.nebula.vpn.core.connection

import android.content.Context
import android.content.Intent
import app.nebula.vpn.vpn.ProxyVpnService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultVpnServiceController @Inject constructor(
    @ApplicationContext private val context: Context
) : VpnServiceController {

    override suspend fun startVpnService(profileId: String?): Unit = withContext(Dispatchers.Main) {
        val intent = Intent(context, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_START
            if (profileId != null) {
                putExtra(ProxyVpnService.EXTRA_PROFILE_ID, profileId)
            }
        }
        context.startForegroundService(intent)
        Unit
    }

    override suspend fun switchVpnService(profileId: String): Unit = withContext(Dispatchers.Main) {
        val intent = Intent(context, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_SWITCH
            putExtra(ProxyVpnService.EXTRA_PROFILE_ID, profileId)
        }
        context.startService(intent)
        Unit
    }

    override suspend fun stopVpnService(timeoutMs: Long): Unit = withContext(Dispatchers.Main) {
        val intent = Intent(context, ProxyVpnService::class.java).apply {
            action = ProxyVpnService.ACTION_STOP
        }
        context.startService(intent)
        Unit
    }
}
