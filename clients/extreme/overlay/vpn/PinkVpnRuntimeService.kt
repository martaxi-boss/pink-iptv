package com.pinkiptv.extreme

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** Required Android foreground notice; there are no client VPN actions or settings. */
class PinkVpnRuntimeService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("pink_connection", "PINK IPTV", NotificationManager.IMPORTANCE_LOW))
        startForeground(5301, NotificationCompat.Builder(this, "pink_connection")
            .setSmallIcon(applicationInfo.icon).setContentTitle("PINK IPTV")
            .setContentText("Ligação PINK em execução").setOngoing(true).build())
        PinkVpnRuntime.get(this)
        return START_NOT_STICKY
    }
}
