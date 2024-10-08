package org.librarysimplified.audiobook.time_tracking

import io.reactivex.Observable
import java.util.concurrent.CompletableFuture

/**
 * The time tracking interface. Publishes a stream of time segments.
 */

interface PlayerTimeTrackerType : AutoCloseable {

  val timeSegments: Observable<PlayerTimeTracked>

  fun bookOpened(
    bookTrackingId: String
  ): CompletableFuture<Void>

  fun bookClosed(): CompletableFuture<Void>

  fun bookPlaybackStarted(
    bookTrackingId: String,
    rate: Double
  ): CompletableFuture<Void>

  fun bookPlaybackRateChanged(
    bookTrackingId: String,
    rate: Double
  ): CompletableFuture<Void>

  fun bookPlaybackPaused(
    bookTrackingId: String,
    rate: Double
  ): CompletableFuture<Void>
}
