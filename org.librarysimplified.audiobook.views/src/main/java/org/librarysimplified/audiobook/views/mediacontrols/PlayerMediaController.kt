package org.librarysimplified.audiobook.views.mediacontrols

import android.app.Application
import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import org.slf4j.LoggerFactory

object PlayerMediaController {

  private val logger =
    LoggerFactory.getLogger(PlayerMediaController::class.java)

  @Volatile
  private var controller: MediaController? = null

  fun start(context: Application) {
    this.logger.debug("Starting media controller...")

    PlayerMediaFacade.start(context)

    val future =
      MediaController.Builder(
        context,
        SessionToken(context, ComponentName(context, PlayerService::class.java))
      ).buildAsync()

    future.addListener({ this.startCompleted(future) }, MoreExecutors.directExecutor())
  }

  private fun startCompleted(
    future: ListenableFuture<MediaController>
  ) {
    try {
      this.logger.debug("Media controller startup completed")
      this.controller = future.get()
    } catch (e: Throwable) {
      this.logger.error("Media controller startup failed: ", e)
    }
  }

  fun stop() {
    this.logger.debug("Stopping media controller...")
    this.controller?.stop()
    this.controller = null
  }
}
