package org.librarysimplified.audiobook.views

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class PlayerService : Service() {

  companion object {
    private const val NOTIFICATION_CHANNEL_ID = "AudiobookCommands"
    private const val NOTIFICATION_CHANNEL_NAME = "Audiobook commands"

    private const val ACTION_PLAY = "org.librarysimplified.audiobook.views.action_play"
    private const val ACTION_PAUSE = "org.librarysimplified.audiobook.views.action_pause"
  }

  private lateinit var playerReceiver: PlayerBroadcastReceiver
  private lateinit var playerInfo: PlayerInfoModel

  private val binder = PlayerBinder()

  override fun onBind(intent: Intent?): IBinder {
    return binder
  }

  override fun onCreate() {
    this.playerReceiver =
      PlayerBroadcastReceiver()

    registerReceiver(this.playerReceiver, IntentFilter().apply {
      addAction(ACTION_PAUSE)
      addAction(ACTION_PLAY)
    })

    createNotificationChannel()

    super.onCreate()
  }

  override fun onDestroy() {
    if (!this.playerInfo.player.isClosed) {
      this.playerInfo.player.close()
    }
    unregisterReceiver(playerReceiver)
    super.onDestroy()
  }

  private fun createNotificationChannel() {

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val notificationChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID,
        NOTIFICATION_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_LOW).apply {
        this.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        this.setShowBadge(false)
      }

      notificationManager.createNotificationChannel(notificationChannel)
    }
  }

  private fun updateNotification() {

    val playIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_PLAY),
      PendingIntent.FLAG_UPDATE_CURRENT)
    val pauseIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_PAUSE),
      PendingIntent.FLAG_UPDATE_CURRENT)

    val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
      .setContentTitle(this.playerInfo.bookName)
      .setContentText(this.playerInfo.bookChapterName)
      .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
        .setShowActionsInCompactView(0)
        .setShowCancelButton(false))
      .setSmallIcon(this.playerInfo.smallIcon)
      .setLargeIcon(this.playerInfo.bookCover)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .addAction(
        if (this.playerInfo.isPlaying) {
          NotificationCompat.Action(R.drawable.ic_pause, "Pause", pauseIntent)
        } else {
          NotificationCompat.Action(R.drawable.ic_play, "Play1", playIntent)
        })
      .build()

    startForeground(1, notification)
  }

  fun updatePlayerInfo(playerInfo: PlayerInfoModel) {
    this.playerInfo = playerInfo
    updateNotification()
  }

  inner class PlayerBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
      val action = intent?.action
      if (action == ACTION_PLAY) {
        playerInfo.player.play()
        playerInfo = playerInfo.copy(
          isPlaying = true
        )
        updateNotification()
      } else if (action == ACTION_PAUSE) {
        playerInfo.player.pause()
        playerInfo = playerInfo.copy(
          isPlaying = false
        )
        updateNotification()
      }
    }
  }

  inner class PlayerBinder : Binder() {
    val playerService: PlayerService
      get() = this@PlayerService
  }
}
