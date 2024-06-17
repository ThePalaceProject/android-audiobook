package org.librarysimplified.audiobook.views.mediacontrols

import android.content.Intent
import android.os.IBinder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.slf4j.LoggerFactory

/**
 * The media session service.
 */

class PlayerService : MediaSessionService() {

  private val logger =
    LoggerFactory.getLogger(PlayerService::class.java)

  override fun onCreate() {
    super.onCreate()
    this.logger.debug("onCreate")
  }

  override fun onBind(
    intent: Intent?
  ): IBinder? {
    this.logger.debug("onBind: {}", intent)
    return super.onBind(intent)
  }

  override fun onUnbind(
    intent: Intent?
  ): Boolean {
    this.logger.debug("onUnbind: {}", intent)
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    super.onDestroy()
    this.logger.debug("onDestroy")
  }

  override fun onGetSession(
    controllerInfo: MediaSession.ControllerInfo
  ): MediaSession {
    this.logger.debug("onGetSession: {}", controllerInfo)
    return MediaSession.Builder(this, PlayerMediaFacade)
      .setCallback(PlayerMediaSessionCallback)
      .build()
  }
}
