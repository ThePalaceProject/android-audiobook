package org.librarysimplified.audiobook.views.mediacontrols

import android.content.Context
import android.content.Intent
import androidx.media.session.MediaButtonReceiver
import org.slf4j.LoggerFactory

class PlayerMediaButtonReceiver : MediaButtonReceiver() {

  private val logger =
    LoggerFactory.getLogger(PlayerMediaButtonReceiver::class.java)

  override fun onReceive(
    context: Context?,
    intent: Intent?
  ) {
    try {
      this.logger.debug("onReceive: {}", intent)
      super.onReceive(context, intent)
    } catch (exception: IllegalStateException) {
      this.logger.error("onReceive: ", exception)
    }
  }
}
