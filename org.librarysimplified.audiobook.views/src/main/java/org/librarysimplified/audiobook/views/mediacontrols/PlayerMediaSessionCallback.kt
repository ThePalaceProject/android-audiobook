package org.librarysimplified.audiobook.views.mediacontrols

import androidx.media3.session.MediaSession
import org.slf4j.LoggerFactory

object PlayerMediaSessionCallback : MediaSession.Callback {

  private val logger =
    LoggerFactory.getLogger(PlayerMediaSessionCallback::class.java)

  override fun onConnect(
    session: MediaSession,
    controller: MediaSession.ControllerInfo
  ): MediaSession.ConnectionResult {
    this.logger.debug("onConnect")
    return super.onConnect(session, controller)
  }

  override fun onPostConnect(
    session: MediaSession,
    controller: MediaSession.ControllerInfo
  ) {
    this.logger.debug("onPostConnect")
    super.onPostConnect(session, controller)
  }

  override fun onDisconnected(
    session: MediaSession,
    controller: MediaSession.ControllerInfo
  ) {
    this.logger.debug("onDisconnected")
    super.onDisconnected(session, controller)
  }
}
