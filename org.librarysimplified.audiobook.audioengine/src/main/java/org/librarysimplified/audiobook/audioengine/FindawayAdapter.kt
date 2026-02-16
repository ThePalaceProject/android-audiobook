package org.librarysimplified.audiobook.audioengine

import io.audioengine.mobile.AudioEngine
import io.audioengine.mobile.PlayRequest
import io.audioengine.mobile.PlaybackEvent
import io.audioengine.mobile.PlayerState
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposables
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerBookmarkKind
import org.librarysimplified.audiobook.api.PlayerBookmarkMetadata
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackBuffering
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPaused
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPreparing
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackProgressUpdate
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStarted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStopped
import org.librarysimplified.audiobook.api.PlayerPauseReason
import org.librarysimplified.audiobook.api.PlayerPlaybackIntention
import org.librarysimplified.audiobook.api.PlayerPlaybackRate
import org.librarysimplified.audiobook.api.PlayerPlaybackStatus
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloaded
import org.librarysimplified.audiobook.manifest.api.PlayerManifestPositionMetadata
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOCItem
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsReadingOrderItem
import org.slf4j.Logger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An adapter class that delegates operations to a real AudioEngine, but has extra capabilities
 * such as player offset tracking, event publications, etc.
 */

class FindawayAdapter(
  private val logger: Logger,
  private val id: String,
  private val book: FindawayAudioBook,
  private val events: Subject<PlayerEvent>,
  private val engine: AudioEngine,
  private val intention: () -> PlayerPlaybackIntention,
) : AutoCloseable {

  var pauseReason: PlayerPauseReason =
    PlayerPauseReason.PAUSE_REASON_INITIALLY_PAUSED

  /**
   * A flag to track whether the user has permitted streaming or not. This is currently unused
   * in the Findaway player as it is next to impossible to get it to stop streaming.
   */

  internal var isStreamingPermitted: Boolean =
    false

  @Volatile
  private var currentPlaybackRateField: PlayerPlaybackRate =
    PlayerPlaybackRate.NORMAL_TIME

  val currentPlaybackRate: PlayerPlaybackRate
    get() = this.currentPlaybackRateField

  @Volatile
  private var playbackStatusField: PlayerPlaybackStatus =
    PlayerPlaybackStatus.PAUSED

  val playbackStatus: PlayerPlaybackStatus
    get() = this.playbackStatusField

  private val closed =
    AtomicBoolean(false)
  private val subscriptions =
    CompositeDisposable()

  private val stateSubject =
    BehaviorSubject.create<FindawayPlayerPlaybackStatusTransition>()
      .toSerialized()

  @Volatile
  private var mostRecentPosition: FindawayPlayerPosition

  /**
   * A flag that indicates whether the current reading order item is being read from local storage,
   * or is being streamed from the network. The Findaway player is capable of switching between
   * streaming and reading from local storage, so this flag has to be set or unset fairly
   * frequently based on what the underlying player says it is doing.
   */

  @Volatile
  internal var isStreamingNow: Boolean =
    false

  init {
    this.mostRecentPosition =
      FindawayPlayerPosition(
        readingOrderItem = this.book.readingOrder.first(),
        readingOrderItemOffsetMilliseconds = PlayerMillisecondsReadingOrderItem(0L),
        part = this.book.readingOrder.first().itemManifest.part,
        chapter = this.book.readingOrder.first().itemManifest.sequence,
        tocItem = this.tocItemFor(this.book.readingOrder.first().id, PlayerMillisecondsReadingOrderItem(0L)),
        totalBookDurationRemaining = Duration.millis(0L)
      )

    val playerEventSub =
      this.engine.playbackEngine.events().subscribe(
        { event -> this.onPlaybackEvent(event) },
        { error -> this.onPlaybackError(error) })

    this.subscriptions.add(
      Disposables.fromRunnable {
        playerEventSub.unsubscribe()
      }
    )

    val playerStateSub =
      this.engine.playbackEngine.state.subscribe(
        { state -> this.onPlaybackState(state) },
        { error -> this.onPlaybackError(error) })

    this.subscriptions.add(
      Disposables.fromRunnable {
        playerStateSub.unsubscribe()
      }
    )
  }

  private fun tocItemFor(
    readingOrderID: PlayerManifestReadingOrderID,
    offsetMilliseconds: PlayerMillisecondsReadingOrderItem
  ): PlayerManifestTOCItem {
    return this.book.tableOfContents.lookupTOCItem(readingOrderID, offsetMilliseconds)
  }

  private fun publishError(
    exception: Throwable?,
    code: Int
  ) {
    val thrown =
      if (exception is Exception) {
        exception
      } else {
        Exception(exception)
      }

    this.events.onNext(
      PlayerEvent.PlayerEventError(
        palaceId = this.book.palaceId,
        readingOrderItem = this.mostRecentPosition.readingOrderItem,
        offsetMilliseconds = this.mostRecentPosition.readingOrderItemOffsetMilliseconds,
        exception = thrown,
        errorCode = code,
        errorCodeName = "FINDAWAY_$code"
      )
    )
  }

  private fun onPlaybackState(
    state: PlayerState?
  ) {
    if (state == null) {
      return
    }

    return when (state) {
      PlayerState.IDLE -> {
        // Nothing to do
      }

      PlayerState.INITIALIZED -> {
        // Nothing to do
      }

      PlayerState.PREPARING -> {
        // Nothing to do
      }

      PlayerState.PREPARED -> {
        // Nothing to do
      }

      PlayerState.BUFFERING -> {
        // Nothing to do
      }

      PlayerState.PLAYING -> {
        // Nothing to do
      }

      PlayerState.PAUSED -> {
        // Nothing to do
      }

      PlayerState.STOPPED -> {
        // Nothing to do
      }

      PlayerState.RELEASED -> {
        // Nothing to do
      }

      PlayerState.ERROR -> {
        // Nothing to do
      }

      PlayerState.COMPLETED -> {
        // Nothing to do
      }
    }
  }

  private fun onPlaybackError(
    error: Throwable?
  ) {
    if (error == null) {
      return
    }

    this.logger.error("[{}]: onPlaybackError: ", this.id, error)
    this.publishError(error, 0x4E590001)
  }

  private fun onPlaybackEvent(
    event: PlaybackEvent?
  ) {
    if (event == null) {
      return
    }

    /*
     * Check that the content ID in the given event matches that of the current book.
     * This is sheer paranoia: The root cause of https://jira.nypl.org/browse/SIMPLY-1505
     * was that players weren't correctly unsubscribing from the playback engine, and so
     * were staying active and were receiving events intended for other players and books.
     *
     * We can only perform this check if the content is actually present in the event.
     */

    val content = event.content
    if (content != null) {
      val receivedId = content.id
      val expectedId = this.book.findawayManifest.fulfillmentId
      if (receivedId != expectedId) {
        this.logger.debug(
          "[{}]: onPlaybackEvent: ignoring event for content id {} (expected {})",
          this.id,
          receivedId,
          expectedId
        )
      }
    }

    this.saveLocation()

    this.isStreamingNow =
      this.mostRecentPosition.readingOrderItem.downloadStatus !is PlayerReadingOrderItemDownloaded

    return when (event.code) {
      PlaybackEvent.PLAYBACK_STARTED -> {
        this.onPlaybackEventPlaybackStarted()
      }

      PlaybackEvent.CHAPTER_PLAYBACK_COMPLETED -> {
        this.onPlaybackEventChapterCompleted()
      }

      PlaybackEvent.PLAYBACK_ENDED -> {
        this.onPlaybackEventPlaybackStopped()
      }

      PlaybackEvent.PLAYBACK_PAUSED -> {
        this.onPlaybackEventPlaybackPaused()
      }

      PlaybackEvent.PLAYBACK_STOPPED -> {
        this.onPlaybackEventPlaybackStopped()
      }

      PlaybackEvent.PLAYBACK_PREPARING -> {
        this.onPlaybackEventPreparing()
      }

      PlaybackEvent.PLAYBACK_BUFFERING_STARTED -> {
        this.onPlaybackEventBufferingStarted()
      }

      PlaybackEvent.PLAYBACK_BUFFERING_ENDED -> {
        this.onPlaybackEventBufferingEnded()
      }

      PlaybackEvent.PLAYBACK_RATE_CHANGE -> {
        this.logger.debug(
          "[{}]: onPlaybackEvent: playback rate changed to {}",
          this.id,
          event.speed
        )
        this.currentPlaybackRateField = this.playbackRateFromSpeed(event.speed)
        this.events.onNext(PlayerEvent.PlayerEventPlaybackRateChanged(palaceId = this.book.palaceId, this.currentPlaybackRate))
      }

      PlaybackEvent.SEEK_COMPLETE -> {
        this.logger.debug("[{}]: onPlaybackEvent: seek complete", this.id)
      }

      PlaybackEvent.PLAYBACK_PROGRESS_UPDATE -> {
        this.onPlaybackEventPlaybackProgressUpdate()
      }

      PlaybackEvent.UNKNOWN_PLAYBACK_ERROR -> {
        this.publishError(event, event.code ?: FindawayPlayer.NO_SENSIBLE_ERROR_CODE)
      }

      PlaybackEvent.UNABLE_TO_ACQUIRE_AUDIO_FOCUS -> {
        this.logger.debug("[{}]: onPlaybackEvent: unable to acquire audio focus", this.id)
        this.publishError(event, event.code ?: FindawayPlayer.NO_SENSIBLE_ERROR_CODE)
      }

      PlaybackEvent.ERROR_PREPARING_PLAYER -> {
        this.logger.debug("[{}]: onPlaybackEvent: error preparing player", this.id)
        this.publishError(event, event.code ?: FindawayPlayer.NO_SENSIBLE_ERROR_CODE)
      }

      PlaybackEvent.NO_CURRENT_CONTENT -> {
        this.logger.debug("[{}]: onPlaybackEvent: no current content", this.id)
      }

      PlaybackEvent.NO_CURRENT_CHAPTER -> {
        this.logger.debug("[{}]: onPlaybackEvent: no current chapter", this.id)
      }

      else -> {
        this.logger.debug(
          "[{}]: onPlaybackEvent: unrecognized playback event code: {}",
          this.id,
          event.code
        )
        if (event.isError) {
          this.publishError(event, event.code ?: FindawayPlayer.NO_SENSIBLE_ERROR_CODE)
        } else {
          // Nothing to do!
        }
      }
    }
  }

  private fun playbackRateFromSpeed(
    speed: Float?
  ): PlayerPlaybackRate {
    fun almostEqual(
      value: Float,
      other: Float
    ): Boolean {
      return Math.abs(value - other) < 0.1
    }

    if (speed != null) {
      if (almostEqual(speed, 0.75f)) {
        return PlayerPlaybackRate.THREE_QUARTERS_TIME
      }
      if (almostEqual(speed, 1.25f)) {
        return PlayerPlaybackRate.ONE_AND_A_QUARTER_TIME
      }
      if (almostEqual(speed, 1.5f)) {
        return PlayerPlaybackRate.ONE_AND_A_HALF_TIME
      }
      if (almostEqual(speed, 2.0f)) {
        return PlayerPlaybackRate.DOUBLE_TIME
      }
    }
    return PlayerPlaybackRate.NORMAL_TIME
  }

  private fun onPlaybackEventBufferingEnded() {
    // Nothing to do
  }

  private fun positionMetadataFor(
    position: FindawayPlayerPosition
  ): PlayerManifestPositionMetadata {
    val bookProgressEstimate =
      position.readingOrderItem.index.toDouble() / this.book.readingOrder.size.toDouble()

    val itemPosition =
      Duration.millis(position.readingOrderItemOffsetMilliseconds.value)
    val chapterDuration =
      position.readingOrderItem.duration
    val itemRemaining =
      chapterDuration.minus(itemPosition)

    val chapterProgressEstimate =
      position.readingOrderItemOffsetMilliseconds.value.toDouble() / chapterDuration.millis.toDouble()

    return PlayerManifestPositionMetadata(
      tocItem = position.tocItem,
      totalRemainingBookTime = position.totalBookDurationRemaining,
      chapterProgressEstimate = chapterProgressEstimate,
      bookProgressEstimate = bookProgressEstimate,
      tocItemPosition = itemPosition,
      tocItemRemaining = itemRemaining
    )
  }

  private fun onPlaybackEventBufferingStarted() {
    this.logger.debug("[{}]: onPlaybackEvent: playback buffering started", this.id)

    this.playbackStatusField =
      PlayerPlaybackStatus.BUFFERING
    val position =
      this.mostRecentPosition
    val positionMetadata =
      this.positionMetadataFor(position)

    this.events.onNext(
      PlayerEventPlaybackBuffering(
        palaceId = this.book.palaceId,
        isStreaming = this.isStreamingNow,
        offsetMilliseconds = position.readingOrderItemOffsetMilliseconds,
        positionMetadata = positionMetadata,
        readingOrderItem = position.readingOrderItem,
      )
    )
  }

  private fun onPlaybackEventPreparing() {
    this.logger.debug("[{}]: onPlaybackEventPreparing", this.id)

    val position =
      this.mostRecentPosition
    val positionMetadata =
      this.positionMetadataFor(position)

    this.events.onNext(
      PlayerEventPlaybackPreparing(
        palaceId = this.book.palaceId,
        isStreaming = this.isStreamingNow,
        offsetMilliseconds = position.readingOrderItemOffsetMilliseconds,
        positionMetadata = positionMetadata,
        readingOrderItem = position.readingOrderItem,
      )
    )
  }

  private fun onPlaybackEventPlaybackPaused() {
    this.logger.debug("[{}]: onPlaybackEventPlaybackPaused", this.id)

    this.playbackStatusField =
      PlayerPlaybackStatus.PAUSED
    val position =
      this.mostRecentPosition
    val positionMetadata =
      this.positionMetadataFor(position)

    this.events.onNext(
      PlayerEventPlaybackPaused(
        palaceId = this.book.palaceId,
        isStreaming = this.isStreamingNow,
        readingOrderItemOffsetMilliseconds = position.readingOrderItemOffsetMilliseconds,
        positionMetadata = positionMetadata,
        readingOrderItem = position.readingOrderItem,
        reason = this.pauseReason
      )
    )
  }

  private fun onPlaybackEventPlaybackStopped() {
    this.logger.debug("[{}]: onPlaybackEventPlaybackStopped", this.id)

    this.playbackStatusField =
      PlayerPlaybackStatus.PAUSED
    val position =
      this.mostRecentPosition
    val positionMetadata =
      this.positionMetadataFor(position)

    this.events.onNext(
      PlayerEventPlaybackStopped(
        palaceId = this.book.palaceId,
        isStreaming = this.isStreamingNow,
        readingOrderItemOffsetMilliseconds = position.readingOrderItemOffsetMilliseconds,
        positionMetadata = positionMetadata,
        readingOrderItem = position.readingOrderItem,
        reason = this.pauseReason
      )
    )
  }

  private fun onPlaybackEventChapterCompleted() {
    this.logger.debug("[{}]: onPlaybackEventChapterCompleted", this.id)

    val position =
      this.mostRecentPosition
    val positionMetadata =
      this.positionMetadataFor(position)

    this.events.onNext(
      PlayerEvent.PlayerEventWithPosition.PlayerEventChapterCompleted(
        palaceId = this.book.palaceId,
        isStreaming = this.isStreamingNow,
        positionMetadata = positionMetadata,
        readingOrderItem = position.readingOrderItem,
      )
    )
  }

  private fun onPlaybackEventPlaybackStarted() {
    this.logger.debug("[{}]: onPlaybackEventPlaybackStarted", this.id)

    this.engine.playbackEngine.speed =
      this.currentPlaybackRate.speed.toFloat()
    this.playbackStatusField =
      PlayerPlaybackStatus.PLAYING

    val position =
      this.mostRecentPosition
    val positionMetadata =
      this.positionMetadataFor(position)

    this.events.onNext(
      PlayerEventPlaybackStarted(
        palaceId = this.book.palaceId,
        isStreaming = this.isStreamingNow,
        offsetMilliseconds = position.readingOrderItemOffsetMilliseconds,
        positionMetadata = positionMetadata,
        readingOrderItem = position.readingOrderItem,
      )
    )
  }

  private fun onPlaybackEventPlaybackProgressUpdate() {
    this.logger.debug("[{}]: onPlaybackEventPlaybackProgressUpdate", this.id)

    val position =
      this.mostRecentPosition
    val positionMetadata =
      this.positionMetadataFor(position)

    this.events.onNext(
      PlayerEventPlaybackProgressUpdate(
        palaceId = this.book.palaceId,
        isStreaming = this.isStreamingNow,
        offsetMilliseconds = position.readingOrderItemOffsetMilliseconds,
        positionMetadata = positionMetadata,
        readingOrderItem = position.readingOrderItem,
      )
    )
  }

  private fun saveLocation() {
    val chapter =
      this.engine.playbackEngine.chapter ?: return
    val offsetMilliseconds =
      PlayerMillisecondsReadingOrderItem(this.engine.playbackEngine.position)

    this.logger.debug(
      "[{}]: saveLocation: {} {} {} {}",
      this.id,
      chapter.part,
      chapter.chapter,
      chapter.duration,
      offsetMilliseconds
    )

    /*
     * The current releases of AudioEngine will often report `(0, 0)` for part and chapter
     * indices. These almost inevitably don't actually exist in the manifest, so we simply
     * log a warning and ignore the event. A corrected event will almost certainly be published
     * very soon after this one.
     */

    val readingOrderItem =
      this.book.readingOrderForPartAndSequence(
        part = chapter.part,
        sequence = chapter.chapter
      )

    if (readingOrderItem == null) {
      this.logger.warn(
        "[{}]: No reading order item for chapter ${chapter.chapter}, part ${chapter.part}",
        this.id
      )
      return
    }

    val tocItem =
      this.tocItemFor(readingOrderItem.id, offsetMilliseconds)

    val totalRemaining =
      this.book.tableOfContents.totalDurationRemaining(
        readingOrderItemID = readingOrderItem.id,
        readingOrderItemOffsetMilliseconds = offsetMilliseconds
      )

    this.mostRecentPosition =
      FindawayPlayerPosition(
        readingOrderItem = readingOrderItem,
        readingOrderItemOffsetMilliseconds = offsetMilliseconds,
        part = chapter.part,
        chapter = chapter.chapter,
        tocItem = tocItem,
        totalBookDurationRemaining = totalRemaining
      )
  }

  override fun close() {
    if (this.closed.compareAndSet(false, true)) {
      this.stateSubject.onComplete()
      this.subscriptions.dispose()
    }
  }

  fun broadcastPlaybackPosition() {
    val position =
      this.mostRecentPosition
    val positionMetadata =
      this.positionMetadataFor(position)

    this.events.onNext(
      PlayerEventPlaybackProgressUpdate(
        palaceId = this.book.palaceId,
        isStreaming = this.isStreamingNow,
        offsetMilliseconds = position.readingOrderItemOffsetMilliseconds,
        positionMetadata = positionMetadata,
        readingOrderItem = position.readingOrderItem,
      )
    )
  }

  fun play(
    playhead: PlayerPosition
  ) {
    val item =
      this.book.readingOrderByID[playhead.readingOrderID]!!

    val request =
      PlayRequest(
        license = this.book.findawayManifest.licenseId,
        contentId = this.book.findawayManifest.fulfillmentId,
        part = item.itemManifest.part,
        chapter = item.itemManifest.sequence,
        position = playhead.offsetMilliseconds.value
      )

    this.engine.playbackEngine.play(request)
    this.engine.playbackEngine.speed = this.currentPlaybackRate.speed.toFloat()
  }

  fun playFromCurrentPosition() {
    val position = this.mostRecentPosition
    this.play(
      PlayerPosition(
        position.readingOrderItem.id,
        position.readingOrderItemOffsetMilliseconds
      )
    )
  }

  fun bookmark() {
    val position =
      this.mostRecentPosition
    val positionMetadata =
      this.positionMetadataFor(position)

    this.events.onNext(
      PlayerEvent.PlayerEventWithPosition.PlayerEventCreateBookmark(
        palaceId = this.book.palaceId,
        isStreaming = this.isStreamingNow,
        kind = PlayerBookmarkKind.EXPLICIT,
        readingOrderItemOffsetMilliseconds = position.readingOrderItemOffsetMilliseconds,
        positionMetadata = positionMetadata,
        readingOrderItem = position.readingOrderItem,
        bookmarkMetadata = PlayerBookmarkMetadata.fromPositionMetadata(positionMetadata)
      )
    )
  }

  fun skipBack(
    milliseconds: Long
  ) {
    assert(milliseconds < 0) { "Milliseconds must be negative" }

    val position =
      this.mostRecentPosition
    val nextMs =
      position.readingOrderItemOffsetMilliseconds.value + milliseconds

    /*
     * The target time might be before the start of the current chapter.
     */

    if (nextMs < 0) {
      if (position.readingOrderItem.index > 0) {
        this.skipToPreviousChapter()
        return
      }

      /*
       * There isn't a "previous" chapter. Just seek to the start of the current one.
       */

      this.seekTo(PlayerMillisecondsReadingOrderItem(0L))
      return
    }

    /*
     * The seek location is within the current chapter.
     */

    this.seekTo(PlayerMillisecondsReadingOrderItem(nextMs))
  }

  fun skipToPreviousChapter() {
    this.engine.playbackEngine.previousChapter()
  }

  fun skipToNextChapter() {
    this.engine.playbackEngine.nextChapter()
  }

  fun skipForward(
    milliseconds: Long
  ) {
    assert(milliseconds >= 0) { "Milliseconds must not be negative" }

    /*
     * The target time might be beyond the end of the current chapter.
     */

    val position =
      this.mostRecentPosition
    val nextMs =
      position.readingOrderItemOffsetMilliseconds.value + milliseconds

    val duration =
      position.readingOrderItem.duration

    if (nextMs > duration.millis) {
      val nextChapter = position.readingOrderItem.next

      /*
       * There's a next chapter. Seek to the start of the next chapter.
       */

      if (nextChapter != null) {
        this.skipToNextChapter()
        return
      }

      /*
       * There isn't a "next" chapter. Do nothing.
       */

      return
    }

    /*
     * The seek location is within the current chapter.
     */

    this.seekTo(PlayerMillisecondsReadingOrderItem(nextMs))
  }

  fun seekTo(
    milliseconds: PlayerMillisecondsReadingOrderItem
  ) {
    /*
     * The Findaway player will ignore seek requests if it isn't currently playing.
     *
     * Therefore, if we want to make it seek, we have to unpause it, seek, and then
     * possibly pause it again depending on what the current user intention is. Additionally,
     * there isn't an API that says "just start playing from wherever you're currently paused",
     * so to tell the Findaway player to play, you have to tell it exactly _where_ to play.
     * Unfortunately, the Findaway player suffers from high latency, so it's desirable to
     * avoid submitting "play" requests if it can otherwise be avoided. See below...
     */

    val position =
      this.mostRecentPosition

    val request =
      PlayRequest(
        license = this.book.findawayManifest.licenseId,
        contentId = this.book.findawayManifest.fulfillmentId,
        part = position.part,
        chapter = position.chapter,
        position = milliseconds.value
      )

    when (this.intention.invoke()) {
      PlayerPlaybackIntention.SHOULD_BE_PLAYING -> {
        /*
         * If the user's intention is to be playing audio, and the player is already playing,
         * we can use the efficient seekTo() method to go to a specific offset. Otherwise,
         * we have to tell the player to play at a specific location, which might involve
         * chapter changing and network I/O...
         */

        if (this.engine.playbackEngine.isPlaying) {
          this.engine.playbackEngine.seekTo(milliseconds.value)
        } else {
          this.engine.playbackEngine.play(request)
        }
      }

      PlayerPlaybackIntention.SHOULD_BE_STOPPED -> {
        /*
         * If the user's intention is to be paused, and the player is already playing,
         * we can use the efficient seekTo() method to go to a specific offset and then
         * pause the player. Otherwise, we have to tell the player to play at a specific location,
         * which might involve chapter changing and network I/O, and _then_ tell it to pause.
         * This is a worst-case scenario, because we have to actively subscribe to the player
         * events, pause the player when it starts playing, and then avoid leaking a subscription.
         */

        if (this.engine.playbackEngine.isPlaying) {
          this.engine.playbackEngine.seekTo(milliseconds.value)
          this.engine.playbackEngine.pause()
        } else {
          this.engine.playbackEngine.play(request)
            .takeFirst { event -> event.code == PlaybackEvent.PLAYBACK_STARTED }
            .subscribe { _ ->
              this.logger.debug(
                "[{}]: seekTo: Received PLAYBACK_STARTED after play request; pausing!",
                this.id
              )
              this.engine.playbackEngine.pause()
            }
        }
      }
    }
  }

  fun setPlaybackRate(
    value: PlayerPlaybackRate
  ) {
    this.currentPlaybackRateField = value
    this.engine.playbackEngine.speed = value.speed.toFloat()
    this.events.onNext(PlayerEvent.PlayerEventPlaybackRateChanged(palaceId = this.book.palaceId, value))
  }

  fun movePlayheadToLocation(
    location: PlayerPosition
  ) {
    val item =
      this.book.readingOrderByID[location.readingOrderID] ?: return

    val request =
      PlayRequest(
        license = this.book.findawayManifest.licenseId,
        contentId = this.book.findawayManifest.fulfillmentId,
        part = item.itemManifest.part,
        chapter = item.itemManifest.sequence,
        position = location.offsetMilliseconds.value
      )

    this.engine.playbackEngine.play(request)
  }
}
