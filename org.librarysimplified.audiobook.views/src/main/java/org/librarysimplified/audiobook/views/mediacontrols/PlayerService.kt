package org.librarysimplified.audiobook.views.mediacontrols

import android.content.Intent
import android.os.IBinder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * The media session service.
 */

class PlayerService : MediaSessionService() {

  private val logger =
    LoggerFactory.getLogger(PlayerService::class.java)

  private val sessionsIssued =
    mutableMapOf<UUID, MediaSession>()

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
    this.releaseAllSessions()
  }

  private fun releaseAllSessions() {
    for (entry in this.sessionsIssued) {
      try {
        this.logger.debug("Releasing session {}", entry.key)
        entry.value.release()
      } catch (e: Throwable) {
        this.logger.debug("Failed to release media session {}: ", entry.key, e)
      }
    }
    this.sessionsIssued.clear()
  }

  override fun onGetSession(
    controllerInfo: MediaSession.ControllerInfo
  ): MediaSession {
    this.logger.debug("onGetSession: {}", controllerInfo)

    val sessionID =
      UUID.randomUUID()
    val session =
      MediaSession.Builder(this, PlayerMediaFacade)
        .setId(sessionID.toString())
        .setCallback(PlayerMediaSessionCallback)
        .build()

    this.logger.debug("Issued new session {}", sessionID)
    this.releaseAllSessions()
    this.sessionsIssued[sessionID] = session
    return session
  }
}
