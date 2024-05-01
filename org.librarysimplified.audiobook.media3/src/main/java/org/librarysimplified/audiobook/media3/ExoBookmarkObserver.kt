package org.librarysimplified.audiobook.media3

import io.reactivex.disposables.Disposable
import org.joda.time.Duration
import org.joda.time.Instant
import org.librarysimplified.audiobook.api.PlayerBookmarkKind
import org.librarysimplified.audiobook.api.PlayerBookmarkMetadata
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerAccessibilityEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventDeleteBookmark
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventError
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventManifestUpdated
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventPlaybackRateChanged
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventChapterCompleted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventChapterWaiting
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventCreateBookmark
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackBuffering
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPaused
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPreparing
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackProgressUpdate
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStarted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStopped
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackWaitingForAction
import org.librarysimplified.audiobook.api.PlayerType
import org.slf4j.LoggerFactory

/**
 * This class implements an observer that constantly polls the current state of the player
 * and decides whether or not to publish "create a bookmark now" events.
 */

class ExoBookmarkObserver private constructor(
  private val player: PlayerType,
  private val onBookmarkCreate: (PlayerEventCreateBookmark) -> Unit,
  private val isStreamingNow: () -> Boolean,
) : AutoCloseable {

  private val logger =
    LoggerFactory.getLogger(ExoBookmarkObserver::class.java)

  private val bookmarkWaitPeriod =
    Duration.standardSeconds(15L)

  private var subscription: Disposable
  private var timeLastSaved: Instant = Instant.now()

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

      is PlayerEventDeleteBookmark,
      is PlayerAccessibilityEvent,
      is PlayerEventError,
      PlayerEventManifestUpdated,
      is PlayerEventPlaybackRateChanged,
      is PlayerEventChapterCompleted,
      is PlayerEventChapterWaiting,
      is PlayerEventCreateBookmark,
      is PlayerEventPlaybackPreparing,
      is PlayerEventPlaybackBuffering,
      is PlayerEventPlaybackPaused,
      is PlayerEventPlaybackStarted,
      is PlayerEventPlaybackStopped,
      is PlayerEventPlaybackWaitingForAction ->
        Unit
    }
  }

  private fun onPlayerProgressUpdate(event: PlayerEventPlaybackProgressUpdate) {
    /*
     * Paranoia: Do not allow users to overwrite bookmarks unless they've listened to
     * at least a few seconds of the chapter.
     */

    if (event.offsetMilliseconds < 3_000L) {
      return
    }

    /*
     * Do not create bookmarks any more frequently than the waiting period.
     */

    val timeNow: Instant = Instant.now()
    if (timeNow.minus(this.bookmarkWaitPeriod).isAfter(this.timeLastSaved)) {
      this.timeLastSaved = timeNow
      this.onBookmarkCreate(
        PlayerEventCreateBookmark(
          readingOrderItem = event.readingOrderItem,
          offsetMilliseconds = event.offsetMilliseconds,
          positionMetadata = event.positionMetadata,
          kind = PlayerBookmarkKind.LAST_READ,
          isStreaming = this.isStreamingNow(),
          bookmarkMetadata = PlayerBookmarkMetadata.fromPositionMetadata(event.positionMetadata)
        )
      )
    }
  }

  companion object {
    fun create(
      player: PlayerType,
      onBookmarkCreate: (PlayerEventCreateBookmark) -> Unit,
      isStreamingNow: () -> Boolean
    ): ExoBookmarkObserver {
      return ExoBookmarkObserver(
        player = player,
        onBookmarkCreate = onBookmarkCreate,
        isStreamingNow = isStreamingNow
      )
    }
  }

  override fun close() {
    this.logger.debug("closing")
    this.subscription.dispose()
  }
}
