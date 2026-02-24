package com.example.again

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ForegroundService : Service() {

    private lateinit var notificationManager: NotificationManager

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val data = intent?.getStringExtra("data") ?: return
            updateNotification(data)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, "tracker_channel")
            .setContentTitle("Tracker Running")
            .setContentText("Monitoring user actions")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()

        startForeground(1, notification)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, IntentFilter("ACTION_USER_EVENT"), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, IntentFilter("ACTION_USER_EVENT"))
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "tracker_channel",
            "Tracker Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification(data: String) {
        val notification = NotificationCompat.Builder(this, "tracker_channel")
            .setContentTitle("Latest User Action")
            .setContentText(data)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
        notificationManager.notify(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }
}