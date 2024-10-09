package org.librarysimplified.audiobook.time_tracking

import io.reactivex.Observable
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import java.util.concurrent.CompletableFuture

/**
 * The time tracking interface. Publishes a stream of time segments.
 */

interface PlayerTimeTrackerType : AutoCloseable {

  val timeSegments: Observable<PlayerTimeTracked>

  fun bookOpened(
    bookTrackingId: PlayerPalaceID
  ): CompletableFuture<Void>

  fun bookClosed(): CompletableFuture<Void>

  fun bookPlaybackStarted(
    bookTrackingId: PlayerPalaceID,
    rate: Double
  ): CompletableFuture<Void>

  fun bookPlaybackRateChanged(
    bookTrackingId: PlayerPalaceID,
    rate: Double
  ): CompletableFuture<Void>

  fun bookPlaybackPaused(
    bookTrackingId: PlayerPalaceID,
    rate: Double
  ): CompletableFuture<Void>
}
