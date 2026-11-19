package com.example.flashshare.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.flashshare.model.TransferStatus
import com.example.flashshare.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TransferService : Service() {

  companion object {
    const val CHANNEL_ID = "flashshare_transfers"
    const val NOTIFICATION_ID = 1042
    private const val ACTION_START = "com.flashshare.offline.ACTION_START_SERVICE"
    private const val ACTION_STOP = "com.flashshare.offline.ACTION_STOP_SERVICE"

    fun start(context: Context) {
      val intent = Intent(context, TransferService::class.java).apply { action = ACTION_START }
      runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      }
    }

    fun stop(context: Context) {
      val intent = Intent(context, TransferService::class.java).apply { action = ACTION_STOP }
      runCatching { context.stopService(intent) }
    }
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var observeJob: Job? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return START_NOT_STICKY
    }

    val initial = buildNotification(
      title = "FlashShare Ready",
      content = "Local offline transfer server active",
      progress = -1,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        initial,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
      )
    } else {
      startForeground(NOTIFICATION_ID, initial)
    }

    observeTransfers()
    return START_STICKY
  }

  private fun observeTransfers() {
    observeJob?.cancel()
    val manager = FlashShareContainer.get(applicationContext).transferManager
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    observeJob = scope.launch {
      manager.transfers.collectLatest { items ->
        val active = items.filter { it.status == TransferStatus.IN_PROGRESS }
        val notification = if (active.isEmpty()) {
          buildNotification(
            title = "FlashShare Server Running",
            content = "Waiting for local Wi-Fi or Hotspot transfers",
            progress = -1,
          )
        } else {
          val first = active.first()
          val totalSpeed = active.sumOf { it.speedBytesPerSec }
          val avgPct = (active.map { it.progress }.average() * 100).toInt().coerceIn(0, 100)
          val title = if (active.size == 1) first.fileName else "${active.size} active transfers"
          val detail = "${avgPct}% • ${Formatters.formatSpeed(totalSpeed)}"
          buildNotification(title = title, content = detail, progress = avgPct)
        }
        runCatching { nm.notify(NOTIFICATION_ID, notification) }
      }
    }
  }

  private fun buildNotification(title: String, content: String, progress: Int): Notification {
    val openIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      openIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val builder = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_upload)
      .setContentTitle(title)
      .setContentText(content)
      .setContentIntent(pendingIntent)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
    if (progress in 0..100) {
      builder.setProgress(100, progress, false)
    }
    return builder.build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        getString(R.string.notification_channel_name),
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = getString(R.string.notification_channel_description)
        setShowBadge(false)
      }
      val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      nm.createNotificationChannel(channel)
    }
  }

  override fun onDestroy() {
    observeJob?.cancel()
    scope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
