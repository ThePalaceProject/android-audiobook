package org.librarysimplified.audiobook.time_tracking

import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

/**
 * A segment of time the user spent listening to a book.
 */

data class PlayerTimeTracked(
  val id: UUID,
  val bookTrackingId: PlayerPalaceID,
  val timeStarted: OffsetDateTime,
  val timeEnded: OffsetDateTime,
  val rate: Double
) {
  val duration: Duration
    get() = Duration.between(this.timeStarted, this.timeEnded)
  val seconds: Long
    get() = this.duration.toMillis() / 1000
}
