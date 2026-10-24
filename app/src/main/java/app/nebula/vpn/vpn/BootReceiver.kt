package app.nebula.vpn.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.nebula.vpn.data.repository.SettingsRepository
import app.nebula.vpn.data.repository.VpnRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var vpnRepository: VpnRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoroutineScope(Dispatchers.IO).launch {
                val autostart = settingsRepository.bootAutostartEnabled.first()
                if (autostart) {
                    val profileId = settingsRepository.selectedProfileId.first()
                    if (profileId != null) {
                        vpnRepository.startVpn(profileId)
                    }
                }
            }
        }
    }
}
