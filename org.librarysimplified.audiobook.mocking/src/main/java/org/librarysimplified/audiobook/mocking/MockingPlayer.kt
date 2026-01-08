package org.librarysimplified.audiobook.mocking

import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStarted
import org.librarysimplified.audiobook.api.PlayerPauseReason
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsReadingOrderItem
import org.librarysimplified.audiobook.api.PlayerPlaybackIntention
import org.librarysimplified.audiobook.api.PlayerPlaybackRate
import org.librarysimplified.audiobook.api.PlayerPlaybackStatus
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.manifest.api.PlayerManifestPositionMetadata
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsAbsolute
import org.slf4j.LoggerFactory

/**
 * A player that does nothing.
 */

class MockingPlayer(private val book: MockingAudioBook) : PlayerType {

  private val log = LoggerFactory.getLogger(MockingPlayer::class.java)

  private val callEvents = PublishSubject.create<String>()
  private val statusEvents = BehaviorSubject.create<PlayerEvent>()
  private var rate: PlayerPlaybackRate = PlayerPlaybackRate.NORMAL_TIME

  val calls: Observable<String> = this.callEvents

  override fun close() {
    this.log.debug("close")
    this.callEvents.onNext("close")
  }

  override val playbackStatus: PlayerPlaybackStatus
    get() = PlayerPlaybackStatus.PAUSED
  override val playbackIntention: PlayerPlaybackIntention
    get() = PlayerPlaybackIntention.SHOULD_BE_STOPPED

  override var playbackRate: PlayerPlaybackRate
    get() = this.rate
    set(value) {
      this.log.debug("playbackRate {}", value)
      this.callEvents.onNext("playbackRate $value")
      this.rate = value
      this.statusEvents.onNext(PlayerEvent.PlayerEventPlaybackRateChanged(book.palaceId, value))
    }

  override val isClosed: Boolean
    get() = false

  override var isStreamingPermitted: Boolean =
    false

  override val events: Observable<PlayerEvent>
    get() = this.statusEvents

  override fun play() {
    this.log.debug("play")
    this.callEvents.onNext("play")
  }

  override fun pause(
    reason: PlayerPauseReason
  ) {
    this.log.debug("pause {}", reason)
    this.callEvents.onNext("pause $reason")
  }

  override fun skipForward() {
    this.log.debug("skipForward")
    this.callEvents.onNext("skipForward")
  }

  override fun skipBack() {
    this.log.debug("skipBack")
    this.callEvents.onNext("skipBack")
  }

  override fun skipPlayhead(milliseconds: Long) {
    this.log.debug("skipPlayhead {}", milliseconds)
    this.callEvents.onNext("skipPlayhead $milliseconds")
  }

  override fun movePlayheadToBookStart() {
    this.log.debug("movePlayheadToBookStart")
    this.movePlayheadToLocation(
      PlayerPosition(this.book.spineItems.first().id, PlayerMillisecondsReadingOrderItem(0L)))
  }

  override fun movePlayheadToAbsoluteTime(milliseconds: PlayerMillisecondsAbsolute) {
    this.log.debug("movePlayheadToAbsoluteTime")
  }

  override fun bookmark() {
    this.log.debug("bookmark")
  }

  override fun bookmarkDelete(bookmark: PlayerBookmark) {
    this.log.debug("bookmarkDelete")
  }

  private fun goToChapter(id: PlayerManifestReadingOrderID, offset: PlayerMillisecondsReadingOrderItem) {
    val element = this.book.spineItems.find { element -> element.id == id }
    if (element != null) {
      this.statusEvents.onNext(
        PlayerEventPlaybackStarted(
          palaceId = book.palaceId,
          readingOrderItem = element,
          offsetMilliseconds = offset,
          positionMetadata = PlayerManifestPositionMetadata(
            tocItem = this.book.tableOfContents.tocItemsInOrder.first(),
            tocItemRemaining = Duration.millis(0),
            tocItemPosition = Duration.millis(0),
            totalRemainingBookTime = Duration.millis(0L),
            chapterProgressEstimate = 0.0,
            bookProgressEstimate = 0.0
          ),
          isStreaming = false
        )
      )
    }
  }

  override fun movePlayheadToLocation(location: PlayerPosition) {
    this.log.debug("movePlayheadToLocation {}", location)
    this.callEvents.onNext("movePlayheadToLocation $location")
    this.goToChapter(location.readingOrderID, location.offsetMilliseconds)
  }
}
