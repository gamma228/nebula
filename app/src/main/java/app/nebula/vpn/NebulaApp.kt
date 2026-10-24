package app.nebula.vpn

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NebulaApp : Application() {

    companion object {
        const val VPN_NOTIFICATION_CHANNEL_ID = "nebula_vpn_status"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "VPN Service"
            val descriptionText = "Displays active VPN connection status and speeds"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(VPN_NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
