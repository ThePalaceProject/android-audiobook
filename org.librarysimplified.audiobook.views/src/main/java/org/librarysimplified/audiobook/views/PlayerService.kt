package org.librarysimplified.audiobook.views

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerAccessibilityEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventDeleteBookmark
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventError
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventManifestUpdated
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventPlaybackRateChanged
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition
import org.slf4j.LoggerFactory

/**
 * A player service responsible for responding to various button presses on lock screens.
 */

class PlayerService : Service() {

  private val logger =
    LoggerFactory.getLogger(PlayerService::class.java)

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

  private var subscriptions: CompositeDisposable = CompositeDisposable()

  @Volatile
  private var mediaSession: MediaSessionCompat? = null

  private val backwardIntent by lazy {
    PendingIntent.getBroadcast(
      this, 0, Intent(ACTION_BACKWARD),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private val forwardIntent by lazy {
    PendingIntent.getBroadcast(
      this, 0, Intent(ACTION_FORWARD),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private val pauseIntent by lazy {
    PendingIntent.getBroadcast(
      this, 0, Intent(ACTION_PAUSE),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private val playIntent by lazy {
    PendingIntent.getBroadcast(
      this, 0, Intent(ACTION_PLAY),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  override fun onBind(intent: Intent?): IBinder? {
    this.logger.debug("onBind")
    return null
  }

  override fun onCreate() {
    this.logger.debug("onCreate")

    this.playerReceiver =
      PlayerBroadcastReceiver()

    val intentFilter = IntentFilter().apply {
      this.addAction(ACTION_BACKWARD)
      this.addAction(ACTION_FORWARD)
      this.addAction(ACTION_PAUSE)
      this.addAction(ACTION_PLAY)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      this.registerReceiver(this.playerReceiver, intentFilter, RECEIVER_EXPORTED)
    } else {
      this.registerReceiver(this.playerReceiver, intentFilter)
    }

    this.createNotificationChannel()

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(PlayerModel.playerEvents.subscribe(this::onPlayerEvent))

    super.onCreate()
  }

  private fun onPlayerEvent(event: PlayerEvent) {
    when (event) {
      is PlayerAccessibilityEvent,
      is PlayerEventError,
      PlayerEventManifestUpdated,
      is PlayerEventDeleteBookmark,
      is PlayerEventPlaybackRateChanged -> {
        // Nothing to do
      }

      is PlayerEventWithPosition -> {
        this.updateNotification(event)
      }
    }
  }

  override fun onDestroy() {
    this.logger.debug("onDestroy")

    this.subscriptions.dispose()
    this.mediaSession?.release()
    this.unregisterReceiver(this.playerReceiver)
    super.onDestroy()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager =
        this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

      val notificationChannel = NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        NOTIFICATION_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        this.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        this.setShowBadge(false)
        this.enableVibration(false)
      }

      notificationManager.createNotificationChannel(notificationChannel)
    }

    val builder =
      NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setOngoing(true)
        .setContentTitle(NOTIFICATION_CHANNEL_NAME)
        .setPriority(NotificationCompat.PRIORITY_LOW)

    val notification = builder.build()
    notification.flags = Notification.FLAG_ONGOING_EVENT

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      this.startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    } else {
      this.startForeground(1001, notification)
    }
  }

  private fun updateNotification(
    event: PlayerEventWithPosition
  ) {
    val session =
      if (this.mediaSession == null) {
        val newSession = MediaSessionCompat(this, PlayerService::class.java.simpleName)
        newSession.isActive = true
        this.mediaSession = newSession
        newSession
      } else {
        this.mediaSession!!
      }

    session.setCallback(object : MediaSessionCompat.Callback() {
      override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
        if (Intent.ACTION_MEDIA_BUTTON == mediaButtonEvent.action) {
          val event: KeyEvent? =
            mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)

          when (event?.keyCode) {
            KEY_CODE_PLAY, KEY_CODE_PAUSE -> {
              PlayerModel.playOrPauseAsAppropriate()
            }

            KEY_CODE_SKIP_TO_NEXT_CHAPTER -> {
              PlayerModel.skipToNext()
            }

            KEY_CODE_SKIP_TO_PREVIOUS_CHAPTER -> {
              PlayerModel.skipToPrevious()
            }

            KEY_CODE_FAST_FORWARD -> {
              PlayerModel.skipForward()
            }

            KEY_CODE_REWIND -> {
              PlayerModel.skipBack()
            }

            null -> {
              // Nothing to do.
            }
          }
        }
        return true
      }
    })

    session.setMetadata(
      MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, event.positionMetadata.tocItem.title)
        .build()
    )

    val contentIntent =
      PendingIntent.getActivity(
        this,
        0,
        PlayerModel.notificationIntentForPlayerService,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

    val notification =
      NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setContentTitle("BOOK NAME!!!")
        .setContentText("BOOK CHAPTER NAME!!!")
        .setStyle(
          androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(false)
            .setMediaSession(session.sessionToken)
        )
        .setContentIntent(contentIntent)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setSilent(true)
        .addAction(
          NotificationCompat.Action(
            R.drawable.round_replay_24, "Backward",
            this.backwardIntent
          )
        )
        .addAction(
          if (PlayerModel.isPlaying) {
            NotificationCompat.Action(R.drawable.round_pause_24, "Pause", this.pauseIntent)
          } else {
            NotificationCompat.Action(R.drawable.baseline_play_arrow_24, "Play", this.playIntent)
          }
        )
        .addAction(NotificationCompat.Action(R.drawable.round_arrow, "Forward", this.forwardIntent))
        .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      this.startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    } else {
      this.startForeground(1, notification)
    }
  }

  private class PlayerBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(
      context: Context?,
      intent: Intent?
    ) {
      when (intent?.action) {
        ACTION_BACKWARD -> PlayerModel.skipBack()
        ACTION_FORWARD -> PlayerModel.skipForward()
        ACTION_PAUSE -> PlayerModel.pause()
        ACTION_PLAY -> PlayerModel.play()
      }
    }
  }
}
