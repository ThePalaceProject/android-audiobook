package org.librarysimplified.audiobook.views.mediacontrols

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.librarysimplified.audiobook.views.PlayerReference
import org.librarysimplified.audiobook.views.R
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

/**
 * The media session service.
 */

class PlayerService : MediaSessionService() {
  private val sessionID: String =
    "palace-audiobook-${sessionIdNext.getAndIncrement()}"

  private val session: AtomicReference<MediaSession> =
    AtomicReference()

  private val logger =
    LoggerFactory.getLogger(PlayerService::class.java)

  companion object {
    private val sessionIdNext =
      java.util.concurrent.atomic
        .AtomicLong()

    private const val DEFAULT_CHANNEL_ID =
      DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID

    private const val DEFAULT_NOTIFICATION_ID =
      DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID
  }

  override fun onCreate() {
    super.onCreate()
    this.logger.debug("{}: onCreate", this)

    this.session.set(
      MediaSession
        .Builder(this, PlayerMediaFacade)
        .setId(this.sessionID)
        .setCallback(PlayerMediaSessionCallback)
        .build()
    )

    PlayerMediaFacade.playerServiceAssign(this)
  }

  override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int
  ): Int {
    // The process holds no player, so nothing can be played; see
    // handleStartWithoutPlayer() for how this situation arises.
    if (!PlayerReference.opIsOpen()) {
      this.logger.warn("{}: Started without an open player; stopping", this)
      this.handleStartWithoutPlayer()
      return START_NOT_STICKY
    }

    return super.onStartCommand(intent, flags, startId)
  }

  override fun toString(): String = "[PlayerService ${this.sessionID}]"

  override fun onBind(intent: Intent?): IBinder? {
    this.logger.debug("{}: onBind: {}", this, intent)
    return super.onBind(intent)
  }

  override fun onUnbind(intent: Intent?): Boolean {
    this.logger.debug("{}: onUnbind: {}", this, intent)
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    this.logger.debug("{}: onDestroy", this)
    this.closeAllResources()
    super.onDestroy()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
    this.logger.debug("{}: onGetSession: {}", this, controllerInfo)
    val sessionNow = this.session.get()
    if (sessionNow == null) {
      this.logger.warn("{}: onGetSession: {}: No available session", this, controllerInfo)
      return null
    }
    return sessionNow
  }

  fun shutDown() {
    this.logger.debug("{}: shutDown", this)
    this.closeAllResources()
  }

  /**
   * Handles a start of this service in a process that holds no player.
   *
   * How this situation arises: while a book is open (playing or paused),
   * the app shows the standard media notification with play/pause and skip
   * buttons; Media3 posts it for this session. The user pauses playback,
   * and the process later dies (e.g. killed under memory pressure). The
   * notification is owned by the system, not by our process, so it
   * survives the death and is still on the screen. When the user taps its
   * play button, Android starts this service with startForegroundService(),
   * which kills the process if startForeground() is not called within a few
   * seconds.
   *
   * There is nothing to play: the player is an in-memory object of this
   * process, and a freshly started process has none. Resuming the book is
   * also not possible from here - that requires the full open pipeline
   * (DRM credentials, manifest fetch and license check), which is driven
   * by the app's UI, not by this service.
   *
   * The action is therefore to call startForeground() with a throwaway
   * notification and stop. The notification is posted under the same ID
   * Media3 uses for the media notification, so the surviving one is
   * replaced and then removed by stopForeground(). Without this, it would
   * remain on the screen, and every tap on it would start and kill the
   * process again.
   */

  private fun handleStartWithoutPlayer() {
    try {
      val notificationManager =
        this.getSystemService(NotificationManager::class.java)

      // Media3 creates this channel only when it builds its own notification,
      // which does not happen on this path.
      if (notificationManager != null) {
        if (notificationManager.getNotificationChannel(DEFAULT_CHANNEL_ID) == null) {
          notificationManager.createNotificationChannel(
            NotificationChannel(
              DEFAULT_CHANNEL_ID,
              "Audiobook",
              NotificationManager.IMPORTANCE_LOW
            )
          )
        }
      }

      val notification =
        NotificationCompat
          .Builder(this, DEFAULT_CHANNEL_ID)
          .setSmallIcon(R.drawable.baseline_book_24)
          .setContentTitle(this.packageManager.getApplicationLabel(this.applicationInfo))
          .build()

      if (Build.VERSION.SDK_INT >= 29) {
        this.startForeground(
          DEFAULT_NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
      } else {
        this.startForeground(DEFAULT_NOTIFICATION_ID, notification)
      }

      this.stopForeground(STOP_FOREGROUND_REMOVE)
      this.stopSelf()
    } catch (e: Throwable) {
      // startForeground() can throw (e.g. POST_NOTIFICATIONS denied); the
      // process will be killed anyway, but stop the service regardless.
      this.logger.error("{}: Failed to call startForeground(): ", this, e)
      try {
        this.stopSelf()
      } catch (_: Throwable) {
        // Nothing more to do.
      }
    }
  }

  private fun closeAllResources() {
    try {
      val s = this.session.get()
      if (s != null) {
        this.logger.debug("{}: Releasing session", this)
        s.release()
      }
      this.session.set(null)
    } catch (e: Throwable) {
      this.logger.debug("session.release() failed: ", e)
    }

    try {
      this.logger.debug("{}: Stopping service", this)
      this.stopSelf()
    } catch (e: Throwable) {
      this.logger.debug("stopSelf() failed: ", e)
    }
  }
}
