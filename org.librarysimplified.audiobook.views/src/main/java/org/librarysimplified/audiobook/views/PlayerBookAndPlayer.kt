package org.librarysimplified.audiobook.views

import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerType
import org.slf4j.LoggerFactory

data class PlayerBookAndPlayer(
  val audioBook: PlayerAudioBookType,
  val player: PlayerType
) : AutoCloseable {

  private val logger =
    LoggerFactory.getLogger(PlayerBookAndPlayer::class.java)

  override fun close() {
    try {
      this.player.close()
    } catch (e: Exception) {
      this.logger.error("Error closing player: ", e)
    }

    try {
      this.audioBook.close()
    } catch (e: Exception) {
      this.logger.error("Error closing book: ", e)
    }
  }
}
