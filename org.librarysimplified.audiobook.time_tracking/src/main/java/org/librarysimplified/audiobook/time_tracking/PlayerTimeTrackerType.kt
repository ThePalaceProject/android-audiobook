package org.librarysimplified.audiobook.time_tracking

import io.reactivex.Observable
import org.librarysimplified.audiobook.api.PlayerOPDSID
import java.util.concurrent.CompletableFuture

/**
 * The time tracking interface. Publishes a stream of time segments.
 */

interface PlayerTimeTrackerType : AutoCloseable {

  val timeSegments: Observable<PlayerTimeTracked>

  fun bookOpened(
    bookTrackingId: PlayerOPDSID
  ): CompletableFuture<Void>

  fun bookClosed(): CompletableFuture<Void>

  fun bookPlaybackStarted(
    bookTrackingId: PlayerOPDSID,
    rate: Double
  ): CompletableFuture<Void>

  fun bookPlaybackRateChanged(
    bookTrackingId: PlayerOPDSID,
    rate: Double
  ): CompletableFuture<Void>

  fun bookPlaybackPaused(
    bookTrackingId: PlayerOPDSID,
    rate: Double
  ): CompletableFuture<Void>
}
