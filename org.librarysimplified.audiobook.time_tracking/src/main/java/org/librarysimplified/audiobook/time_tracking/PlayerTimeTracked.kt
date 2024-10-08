package org.librarysimplified.audiobook.time_tracking

import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

/**
 * A segment of time the user spent listening to a book.
 */

data class PlayerTimeTracked(
  val id: UUID,
  val bookTrackingId: String,
  val timeStarted: OffsetDateTime,
  val timeEnded: OffsetDateTime,
  val rate: Double
) {
  fun duration(): Duration {
    return Duration.between(this.timeStarted, this.timeEnded)
  }
}
