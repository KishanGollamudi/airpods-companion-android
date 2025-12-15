package com.example.airpodscompanion.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.airpodscompanion.data.AirPodsStatus
import com.example.airpodscompanion.data.BluetoothLeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AirPodsScanService : Service() {

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var bleManager: BluetoothLeManager
        private set

    private var lastConnectionState = false

    inner class LocalBinder : Binder() {
        fun getService(): AirPodsScanService = this@AirPodsScanService
    }

    override fun onCreate() {
        super.onCreate()
        bleManager = BluetoothLeManager(this)
        startForegroundWithNotification()

        // Monitor AirPods status for auto play / pause
        bleManager.airPodsStatus
            .onEach { status ->
                handleAutoPlayLogic(status)
            }
            .launchIn(scope)
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            stopSelf()
            return START_NOT_STICKY
        }

        bleManager.startScan()
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val channelId = "airpods_scan_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AirPods Scanning",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AirPods Companion")
            .setContentText("Scanning for AirPods…")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(1, notification)
        }
    }

    private fun handleAutoPlayLogic(status: AirPodsStatus) {
        if (!lastConnectionState && status.isConnected) {
            triggerMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        } else if (lastConnectionState && !status.isConnected) {
            triggerMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        }
        lastConnectionState = status.isConnected
    }

    private fun triggerMediaKey(keyCode: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)

        audioManager.dispatchMediaKeyEvent(eventDown)
        audioManager.dispatchMediaKeyEvent(eventUp)
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.stopScan()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder = binder
}

