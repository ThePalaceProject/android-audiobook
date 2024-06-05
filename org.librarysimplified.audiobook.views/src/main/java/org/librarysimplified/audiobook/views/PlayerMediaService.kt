package org.librarysimplified.audiobook.views

import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.slf4j.LoggerFactory

class PlayerMediaService : Service() {

  private val logger =
    LoggerFactory.getLogger(PlayerMediaService::class.java)

  override fun onBind(
    intent: Intent?
  ): IBinder? {
    this.logger.debug("onBind: {}", intent)
    return null
  }
}
