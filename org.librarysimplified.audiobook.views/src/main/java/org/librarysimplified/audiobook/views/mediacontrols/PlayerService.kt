package org.librarysimplified.audiobook.views.mediacontrols

import android.content.Intent
import android.os.IBinder
import androidx.annotation.UiThread
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.reactivex.disposables.Disposable
import org.librarysimplified.audiobook.views.PlayerModel
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The media session service.
 */

class PlayerService : MediaSessionService() {

  private var subscription: Disposable? = null

  private val closed =
    AtomicBoolean(false)

  private val logger =
    LoggerFactory.getLogger(PlayerService::class.java)

  private val sessionsIssued =
    mutableMapOf<UUID, MediaSession>()

  override fun onCreate() {
    super.onCreate()
    this.logger.debug("onCreate")

    this.subscription =
      PlayerModel.playerServiceCommands.subscribe(this::onPlayerServiceCommand)
  }

  @UiThread
  private fun onPlayerServiceCommand(
    command: PlayerServiceCommand
  ) {
    return when (command) {
      PlayerServiceCommand.PlayerServiceShutDown -> {
        this.doShutdownNow()
      }
    }
  }

  private fun doShutdownNow() {
    if (this.closed.compareAndSet(false, true)) {
      this.logger.debug("Shutting down service…")

      try {
        this.subscription?.dispose()
      } catch (e: Throwable) {
        this.logger.debug("Unsubscribing failed: ", e)
      }

      try {
        this.releaseAllSessions()

        try {
          this.stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Throwable) {
          this.logger.debug("Call to stopForeground() failed: ", e)
        }

        try {
          this.stopSelf()
        } catch (e: Throwable) {
          this.logger.debug("Call to stopSelf() failed: ", e)
        }
      } finally {
        this.logger.debug("Service shutdown completed.")
      }
    } else {
      this.logger.debug("Service is already shut down.")
    }
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
    this.doShutdownNow()
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
