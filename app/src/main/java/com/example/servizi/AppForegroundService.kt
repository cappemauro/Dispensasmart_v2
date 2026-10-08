package com.example.servizi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.R

class AppForegroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DispensaSmart::BackgroundSyncWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max just in case, but usually we just hold it
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Re-acquire wake lock if needed or just keep it
        if (wakeLock?.isHeld != true) {
            wakeLock?.acquire() // no timeout if we want to run forever, or manage it properly
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "sync_channel",
                "Sincronizzazione Background",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene l'app attiva in background per il backup"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "sync_channel")
            .setContentTitle("DispensaSmart in esecuzione")
            .setContentText("Sincronizzazione e backup in background")
            .setSmallIcon(android.R.drawable.stat_notify_sync) 
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
