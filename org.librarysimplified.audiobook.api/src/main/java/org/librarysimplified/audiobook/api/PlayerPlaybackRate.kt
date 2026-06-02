package org.librarysimplified.audiobook.api

import java.util.Locale

/**
 * The playback rate of the player.
 */

data class PlayerPlaybackRate(
  val speed: Double
) {
  val formatted: String
    get() = String.format(Locale.ROOT, "%.2fx", this.speed)

  companion object {
    val RATE_0_5 = PlayerPlaybackRate(0.5)
    val RATE_1 = PlayerPlaybackRate(1.0)
    val RATE_1_25 = PlayerPlaybackRate(1.25)
    val RATE_1_5 = PlayerPlaybackRate(1.5)
    val RATE_2 = PlayerPlaybackRate(2.0)

    val RATE_MIN = RATE_0_5
    val RATE_MAX = RATE_2
  }
}
