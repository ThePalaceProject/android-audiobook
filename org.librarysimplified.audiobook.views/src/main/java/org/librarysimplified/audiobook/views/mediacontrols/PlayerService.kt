package org.librarysimplified.audiobook.views.mediacontrols

import android.content.Intent
import android.os.IBinder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
      java.util.concurrent.atomic.AtomicLong()
  }

  override fun onCreate() {
    super.onCreate()
    this.logger.debug("{}: onCreate", this)

    this.session.set(
      MediaSession.Builder(this, PlayerMediaFacade)
        .setId(this.sessionID)
        .setCallback(PlayerMediaSessionCallback)
        .build()
    )

    PlayerMediaFacade.playerServiceAssign(this)
  }

  override fun toString(): String {
    return "[PlayerService ${this.sessionID}]"
  }

  override fun onBind(
    intent: Intent?
  ): IBinder? {
    this.logger.debug("{}: onBind: {}", this, intent)
    return super.onBind(intent)
  }

  override fun onUnbind(
    intent: Intent?
  ): Boolean {
    this.logger.debug("{}: onUnbind: {}", this, intent)
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    this.logger.debug("{}: onDestroy", this)
    this.closeAllResources()
    super.onDestroy()
  }

  override fun onGetSession(
    controllerInfo: MediaSession.ControllerInfo
  ): MediaSession {
    this.logger.debug("{}: onGetSession: {}", this, controllerInfo)
    return this.session.get()
  }

  fun shutDown() {
    this.logger.debug("{}: shutDown", this)
    this.closeAllResources()
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
