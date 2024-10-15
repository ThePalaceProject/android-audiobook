package org.librarysimplified.audiobook.time_tracking

import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

/**
 * A segment of time the user spent listening to a book.
 */

data class PlayerTimeTracked private constructor(
  val id: UUID,
  val bookTrackingId: PlayerPalaceID,
  val timeStarted: OffsetDateTime,
  val timeEnded: OffsetDateTime,
  val rate: Double
) {
  init {
    val between = Duration.between(timeStarted, timeEnded)
    require(between.toMillis() <= 60_000L) {
      "Tracked time segment ($between) must be <= one minute"
    }
  }

  companion object {
    fun create(
      id: UUID,
      bookTrackingId: PlayerPalaceID,
      timeStarted: OffsetDateTime,
      timeEnded: OffsetDateTime,
      rate: Double
    ): PlayerTimeTracked {
      val diff = Duration.between(timeStarted, timeEnded)
      if (diff.toMillis() / 1000L > 60_000L) {
        return PlayerTimeTracked(
          id = id,
          bookTrackingId = bookTrackingId,
          timeStarted = timeStarted,
          timeEnded = timeStarted.plusSeconds(60L),
          rate = rate
        )
      }
      return PlayerTimeTracked(
        id = id,
        bookTrackingId = bookTrackingId,
        timeStarted = timeStarted,
        timeEnded = timeEnded,
        rate = rate
      )
    }
  }

  val duration: Duration
    get() = Duration.between(this.timeStarted, this.timeEnded)
  val seconds: Long
    get() = this.duration.toMillis() / 1000
}
