package org.librarysimplified.audiobook.mocking

import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStarted
import org.librarysimplified.audiobook.api.PlayerPlaybackIntention
import org.librarysimplified.audiobook.api.PlayerPlaybackRate
import org.librarysimplified.audiobook.api.PlayerPlaybackStatus
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerPositionMetadata
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
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
      this.statusEvents.onNext(PlayerEvent.PlayerEventPlaybackRateChanged(value))
    }

  override val isClosed: Boolean
    get() = false

  override val events: Observable<PlayerEvent>
    get() = this.statusEvents

  override fun play() {
    this.log.debug("play")
    this.callEvents.onNext("play")
  }

  override fun pause() {
    this.log.debug("pause")
    this.callEvents.onNext("pause")
  }

  override fun skipToNextChapter(offset: Long) {
    this.log.debug("skipToNextChapter")
    this.callEvents.onNext("skipToNextChapter")
  }

  override fun skipToPreviousChapter(offset: Long) {
    this.log.debug("skipToPreviousChapter")
    this.callEvents.onNext("skipToPreviousChapter")
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
      PlayerPosition(this.book.spineItems.first().id, 0L))
  }

  override fun seekTo(milliseconds: Long) {
    this.log.debug("seekTo")
  }

  override fun bookmark() {
    this.log.debug("bookmark")
  }

  override fun bookmarkDelete(bookmark: PlayerBookmark) {
    this.log.debug("bookmarkDelete")
  }

  private fun goToChapter(id: PlayerManifestReadingOrderID, offset: Long) {
    val element = this.book.spineItems.find { element -> element.id == id }
    if (element != null) {
      this.statusEvents.onNext(
        PlayerEventPlaybackStarted(
          readingOrderItem = element,
          offsetMilliseconds = offset,
          positionMetadata = PlayerPositionMetadata(
            tocItem = this.book.tableOfContents.tocItemsInOrder.first(),
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
