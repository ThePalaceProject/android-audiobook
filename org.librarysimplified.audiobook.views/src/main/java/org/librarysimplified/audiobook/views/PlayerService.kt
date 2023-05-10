package org.librarysimplified.audiobook.views

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat


class PlayerService : Service() {

  companion object {
    private const val NOTIFICATION_CHANNEL_ID = "Audiobook_Player_Commands"
    private const val NOTIFICATION_CHANNEL_NAME = "Audiobook Player Commands"

    private const val ACTION_BACKWARD = "org.librarysimplified.audiobook.views.action_backward"
    private const val ACTION_FORWARD = "org.librarysimplified.audiobook.views.action_forward"
    private const val ACTION_PAUSE = "org.librarysimplified.audiobook.views.action_pause"
    private const val ACTION_PLAY = "org.librarysimplified.audiobook.views.action_play"

    private const val KEY_CODE_PLAY = 126
    private const val KEY_CODE_PAUSE = 127
    private const val KEY_CODE_SKIP_TO_NEXT_CHAPTER = 87
    private const val KEY_CODE_SKIP_TO_PREVIOUS_CHAPTER = 88
    private const val KEY_CODE_REWIND = 89
    private const val KEY_CODE_FAST_FORWARD = 90

  }

  private lateinit var playerReceiver: PlayerBroadcastReceiver
  private lateinit var playerInfo: PlayerInfoModel

  private var mediaSession: MediaSessionCompat? = null

  private val binder = PlayerBinder()

  private val backwardIntent by lazy {
    PendingIntent.getBroadcast(
      this, 0, Intent(ACTION_BACKWARD),
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }
    )
  }

  private val forwardIntent by lazy {
    PendingIntent.getBroadcast(
      this, 0, Intent(ACTION_FORWARD),
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }
    )
  }

  private val pauseIntent by lazy {
    PendingIntent.getBroadcast(
      this, 0, Intent(ACTION_PAUSE),
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }
    )
  }

  private val playIntent by lazy {
    PendingIntent.getBroadcast(
      this, 0, Intent(ACTION_PLAY),
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }
    )
  }

  override fun onBind(intent: Intent?): IBinder {
    return binder
  }

  override fun onCreate() {
    this.playerReceiver =
      PlayerBroadcastReceiver()

    registerReceiver(this.playerReceiver, IntentFilter().apply {
      addAction(ACTION_BACKWARD)
      addAction(ACTION_FORWARD)
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
    mediaSession?.release()
    unregisterReceiver(playerReceiver)
    super.onDestroy()
  }

  fun createNotificationChannel() {

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val notificationChannel = NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        NOTIFICATION_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_LOW
      )
        .apply {
          this.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
          this.setShowBadge(false)
          this.enableVibration(false)
        }

      notificationManager.createNotificationChannel(notificationChannel)
    }

    val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
      .setOngoing(true)
      .setContentTitle(NOTIFICATION_CHANNEL_NAME)
      .setPriority(NotificationCompat.PRIORITY_LOW)

    val notification = builder.build()
    notification.flags = Notification.FLAG_ONGOING_EVENT
    startForeground(1001, notification)
  }

  private fun updateNotification() {

    if (mediaSession == null) {
      mediaSession = MediaSessionCompat(this, PlayerService::class.java.simpleName)
    }

    mediaSession?.setCallback(object : MediaSessionCompat.Callback() {
      override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
        if (Intent.ACTION_MEDIA_BUTTON == mediaButtonEvent.action) {
          val event: KeyEvent? =
            mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
          Log.d("KeyCode", event?.keyCode.toString())
          if (event?.keyCode == KEY_CODE_PLAY || event?.keyCode == KEY_CODE_PAUSE) {
            if (playerInfo.isPlaying) {
              playerInfo.player.pause()
            } else {
              playerInfo.player.play()
            }
          }
          else if (event?.keyCode == KEY_CODE_SKIP_TO_NEXT_CHAPTER) {
            playerInfo.player.skipToNextChapter(0)
          }
          else if (event?.keyCode == KEY_CODE_SKIP_TO_PREVIOUS_CHAPTER) {
            playerInfo.player.skipToPreviousChapter(0)
          }
          else if (event?.keyCode == KEY_CODE_FAST_FORWARD) {
            playerInfo.player.skipForward()
          }
          else if (event?.keyCode == KEY_CODE_REWIND) {
            playerInfo.player.skipBack()
          }
        }
        return true
      }
    })

    mediaSession?.setMetadata(
      MediaMetadataCompat.Builder()
        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, playerInfo.bookCover)
        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, playerInfo.bookChapterName)
        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, playerInfo.bookName)
        .build()
    )

    val contentIntent = PendingIntent.getActivity(
      this, 0, playerInfo.notificationIntent,
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }
    )

    val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
      .setContentTitle(this.playerInfo.bookName)
      .setContentText(this.playerInfo.bookChapterName)
      .setStyle(
        androidx.media.app.NotificationCompat.MediaStyle()
          .setShowActionsInCompactView(0, 1, 2)
          .setShowCancelButton(false)
          .setMediaSession(mediaSession?.sessionToken)
      )
      .setContentIntent(contentIntent)
      .setSmallIcon(this.playerInfo.smallIcon)
      .setLargeIcon(this.playerInfo.bookCover)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setNotificationSilent()
      .addAction(NotificationCompat.Action(R.drawable.ic_backward, "Backward", backwardIntent))
      .addAction(
        if (this.playerInfo.isPlaying) {
          NotificationCompat.Action(R.drawable.ic_pause, "Pause", pauseIntent)
        } else {
          NotificationCompat.Action(R.drawable.ic_play, "Play", playIntent)
        }
      )
      .addAction(NotificationCompat.Action(R.drawable.ic_forward, "Forward", forwardIntent))
      .build()

    startForeground(1, notification)
  }

  fun updatePlayerInfo(playerInfo: PlayerInfoModel) {
    this.playerInfo = playerInfo
    updateNotification()
  }

  inner class PlayerBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
      when (intent?.action) {
        ACTION_BACKWARD -> {
          playerInfo.player.skipBack()
          updateNotification()
        }
        ACTION_FORWARD -> {
          playerInfo.player.skipForward()
          updateNotification()
        }
        ACTION_PAUSE -> {
          mediaSession?.isActive = false
          playerInfo.player.pause()
          playerInfo = playerInfo.copy(
            isPlaying = false
          )
          updateNotification()
        }
        ACTION_PLAY -> {
          mediaSession?.isActive = true
          playerInfo.player.play()
          playerInfo = playerInfo.copy(
            isPlaying = true
          )
          updateNotification()
        }
      }
    }
  }

  inner class PlayerBinder : Binder() {
    val playerService: PlayerService
      get() = this@PlayerService
  }
}
