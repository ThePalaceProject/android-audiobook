package org.librarysimplified.audiobook.open_access

import org.joda.time.Duration
import org.joda.time.Instant
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventError
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventManifestUpdated
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventPlaybackRateChanged
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventChapterCompleted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventChapterWaiting
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventCreateBookmark
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackBuffering
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackPaused
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackProgressUpdate
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackStarted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackStopped
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackWaitingForAction
import org.librarysimplified.audiobook.api.PlayerType
import org.slf4j.LoggerFactory
import rx.Subscription

class ExoBookmarkObserver private constructor(
  private val player: PlayerType,
  private val onBookmarkCreate: (PlayerEventCreateBookmark) -> Unit
) : AutoCloseable {

  private val logger =
    LoggerFactory.getLogger(ExoBookmarkObserver::class.java)
  private val bookmarkWaitPeriod =
    Duration.standardSeconds(5L)

  private var subscription: Subscription
  private var timeAtLast: Instant? = null

  init {
    this.subscription = this.player.events.subscribe(this::onPlayerEvent)
  }

  private fun onPlayerEvent(event: PlayerEvent) {
    return when (event) {
      is PlayerEventPlaybackProgressUpdate -> {
        if (!this.player.isClosed) {
          this.onPlayerProgressUpdate(event)
        } else {
          Unit
        }
      }

      is PlayerEventError,
      PlayerEventManifestUpdated,
      is PlayerEventPlaybackRateChanged,
      is PlayerEventChapterCompleted,
      is PlayerEventChapterWaiting,
      is PlayerEventCreateBookmark,
      is PlayerEventPlaybackBuffering,
      is PlayerEventPlaybackPaused,
      is PlayerEventPlaybackStarted,
      is PlayerEventPlaybackStopped,
      is PlayerEventPlaybackWaitingForAction ->
        Unit
    }
  }

  private fun onPlayerProgressUpdate(event: PlayerEventPlaybackProgressUpdate) {
    this.logger.debug("onPlayerProgressUpdate: {}", event)

    /*
     * Paranoia: Do not allow users to overwrite bookmarks unless they've listened to
     * at least a few seconds of the chapter.
     */

    if (event.offsetMilliseconds < 100L) {
      return
    }

    /*
     * Do not create bookmarks any more frequently than the waiting period.
     */

    val timeNow = Instant.now()
    val timeLast = this.timeAtLast
    val create = if (timeLast != null) {
      timeNow.minus(this.bookmarkWaitPeriod).isAfter(timeLast)
    } else {
      true
    }

    if (create) {
      this.timeAtLast = timeNow
      this.onBookmarkCreate(PlayerEventCreateBookmark(event.spineElement, event.offsetMilliseconds))
    }
  }

  companion object {
    fun create(
      player: PlayerType,
      onBookmarkCreate: (PlayerEventCreateBookmark) -> Unit
    ): ExoBookmarkObserver {
      return ExoBookmarkObserver(
        player = player,
        onBookmarkCreate = onBookmarkCreate
      )
    }
  }

  override fun close() {
    this.logger.debug("closing")
    this.subscription.unsubscribe()
  }
}
