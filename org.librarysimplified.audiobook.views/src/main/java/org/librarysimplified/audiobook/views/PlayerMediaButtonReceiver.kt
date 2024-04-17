package org.librarysimplified.audiobook.views

import android.content.Context
import android.content.Intent
import androidx.media.session.MediaButtonReceiver
import org.slf4j.LoggerFactory

class PlayerMediaButtonReceiver : MediaButtonReceiver() {

  private val logger =
    LoggerFactory.getLogger(PlayerMediaButtonReceiver::class.java)

  override fun onReceive(context: Context?, intent: Intent?) {
    try {
      super.onReceive(context, intent)
    } catch (exception: IllegalStateException) {
      this.logger.error("Received exception on MediaButtonReceiver: ", exception)
    }
  }
}
