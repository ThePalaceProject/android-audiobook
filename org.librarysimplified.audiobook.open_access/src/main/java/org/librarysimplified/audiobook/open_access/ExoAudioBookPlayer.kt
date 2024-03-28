package org.librarysimplified.audiobook.open_access

import android.app.Application
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposables
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import org.librarysimplified.audiobook.api.PlayerBookmarkKind
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventPlaybackRateChanged
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventChapterCompleted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventChapterWaiting
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackBuffering
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPaused
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackProgressUpdate
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStarted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStopped
import org.librarysimplified.audiobook.api.PlayerPlaybackIntention
import org.librarysimplified.audiobook.api.PlayerPlaybackIntention.SHOULD_BE_PLAYING
import org.librarysimplified.audiobook.api.PlayerPlaybackIntention.SHOULD_BE_STOPPED
import org.librarysimplified.audiobook.api.PlayerPlaybackRate
import org.librarysimplified.audiobook.api.PlayerPlaybackRate.NORMAL_TIME
import org.librarysimplified.audiobook.api.PlayerPlaybackStatus
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloadExpired
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloadFailed
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloading
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemNotDownloaded
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.librarysimplified.audiobook.api.PlayerUIThread.runOnUIThread
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOCItem
import org.librarysimplified.audiobook.open_access.ExoAudioBookPlayer.SkipChapterStatus.SKIP_TO_CHAPTER_NONEXISTENT
import org.librarysimplified.audiobook.open_access.ExoAudioBookPlayer.SkipChapterStatus.SKIP_TO_CHAPTER_NOT_DOWNLOADED
import org.librarysimplified.audiobook.open_access.ExoAudioBookPlayer.SkipChapterStatus.SKIP_TO_CHAPTER_READY
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An ExoPlayer player.
 */

class ExoAudioBookPlayer private constructor(
  private val book: ExoAudioBook,
  private val dataSourceFactory: DataSource.Factory,
  private val exoPlayer: ExoPlayer,
  manifestUpdates: Observable<Unit>,
  private val statusEvents: Subject<PlayerEvent>,
) : PlayerType {

  private val log =
    LoggerFactory.getLogger(ExoAudioBookPlayer::class.java)

  private val closed = AtomicBoolean(false)
  private val zeroBugTracker: ExoPlayerZeroBugDetection
  private val bookmarkObserver: ExoBookmarkObserver
  private val resources = CompositeDisposable()

  private val statusExecutor: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor { r ->
      val thread = Thread(r)
      thread.name = "ExoAudioBookPlayer.status[${thread.id}]"
      thread.priority = Thread.MIN_PRIORITY
      thread
    }

  @Volatile
  private var currentPlaybackRate: PlayerPlaybackRate =
    NORMAL_TIME

  @Volatile
  private var currentReadingOrderElement: ExoReadingOrderItemHandle =
    this.book.readingOrder.first()

  @Volatile
  private var intention: PlayerPlaybackIntention =
    SHOULD_BE_STOPPED

  private val exoAdapter: ExoAdapter =
    ExoAdapter(
      logger = this.log,
      events = this.statusEvents,
      exoPlayer = this.exoPlayer,
      currentReadingOrderItem = {
        this.currentReadingOrderElement
      },
      tocItemFor = { item, time ->
        this.tocItemFor(item.id, time)
      },
      toc = this.book.tableOfContents
    )

  init {
    this.resources.add(Disposables.fromAction(this.statusExecutor::shutdown))

    /*
     * Register a bookmark observer.
     */

    this.bookmarkObserver =
      ExoBookmarkObserver.create(
        player = this,
        onBookmarkCreate = this.statusEvents::onNext
      )
    this.resources.add(Disposables.fromAction(this.bookmarkObserver::close))

    this.zeroBugTracker =
      ExoPlayerZeroBugDetection(this.book.readingOrder)

    /*
     * Register an observer that will periodically ask the adapter for it's current playback
     * position.
     */

    this.exoPlayer.addListener(this.exoAdapter)
    this.statusExecutor.scheduleAtFixedRate({
      runOnUIThread {
        if (this.intention == SHOULD_BE_PLAYING) {
          this.exoAdapter.broadcastPlaybackPosition()
        }
      }
    }, 500L, 500L, TimeUnit.MILLISECONDS)

    /*
     * Subscribe to player status updates.
     */

    this.resources.add(
      this.exoAdapter.stateObservable.subscribe(
        { transition -> this.onPlayerStateChanged(transition) },
        { exception -> this.log.error("Player status error: ", exception) }
      )
    )

    /*
     * Subscribe to notifications of download changes. This is only needed because the
     * player may be waiting for a chapter to be downloaded and needs to know when it can
     * safely play the chapter.
     */

    this.resources.add(
      this.book.readingOrderElementDownloadStatus.subscribe(
        { status -> runOnUIThread { this.onDownloadStatusChanged(status) } },
        { exception -> this.log.error("Download status error: ", exception) }
      )
    )

    /*
     * Subscribe to manifest updates; links in manifests can be replaced.
     */

    this.resources.add(manifestUpdates.subscribe {
      this.onManifestUpdated()
    })
  }

  private fun onPlayerStateChanged(
    state: ExoPlayerPlaybackStatusTransition
  ) {
    PlayerUIThread.checkIsUIThread()

    val toc = this.book.tableOfContents

    when (state.newState) {
      ExoPlayerPlaybackStatus.INITIAL -> {
        if (state.oldState == ExoPlayerPlaybackStatus.PLAYING) {
          val tocItem =
            this.tocItemFor(this.currentReadingOrderElement.id, 0L)

          this.statusEvents.onNext(
            PlayerEventPlaybackStopped(
              readingOrderItem = this.currentReadingOrderElement,
              offsetMilliseconds = 0,
              tocItem = tocItem,
              totalRemainingBookTime = toc.totalDurationRemaining(tocItem, 0L)
            )
          )
        }
      }

      ExoPlayerPlaybackStatus.BUFFERING -> {
        val offsetMilliseconds =
          this.exoAdapter.currentTrackOffsetMilliseconds()
        val tocItem =
          this.tocItemFor(this.currentReadingOrderElement.id, offsetMilliseconds)

        this.statusEvents.onNext(
          PlayerEventPlaybackBuffering(
            readingOrderItem = this.currentReadingOrderElement,
            offsetMilliseconds = offsetMilliseconds,
            tocItem = tocItem,
            totalRemainingBookTime = toc.totalDurationRemaining(tocItem, offsetMilliseconds)
          )
        )
      }

      ExoPlayerPlaybackStatus.PLAYING -> {
        val offsetMilliseconds =
          this.exoAdapter.currentTrackOffsetMilliseconds()
        val tocItem =
          this.tocItemFor(this.currentReadingOrderElement.id, offsetMilliseconds)

        if (state.oldState != ExoPlayerPlaybackStatus.PLAYING) {
          this.statusEvents.onNext(
            PlayerEventPlaybackStarted(
              readingOrderItem = this.currentReadingOrderElement,
              offsetMilliseconds = offsetMilliseconds,
              tocItem = tocItem,
              totalRemainingBookTime = toc.totalDurationRemaining(tocItem, offsetMilliseconds)
            )
          )
        }

        this.statusEvents.onNext(
          PlayerEventPlaybackProgressUpdate(
            readingOrderItem = this.currentReadingOrderElement,
            offsetMilliseconds = offsetMilliseconds,
            tocItem = tocItem,
            totalRemainingBookTime = toc.totalDurationRemaining(tocItem, offsetMilliseconds)
          )
        )
      }

      ExoPlayerPlaybackStatus.PAUSED -> {
        val offsetMilliseconds =
          this.exoAdapter.currentTrackOffsetMilliseconds()
        val tocItem =
          this.tocItemFor(this.currentReadingOrderElement.id, offsetMilliseconds)

        this.statusEvents.onNext(
          PlayerEventPlaybackPaused(
            readingOrderItem = this.currentReadingOrderElement,
            offsetMilliseconds = offsetMilliseconds,
            tocItem = tocItem,
            totalRemainingBookTime = toc.totalDurationRemaining(tocItem, offsetMilliseconds)
          )
        )
      }

      ExoPlayerPlaybackStatus.CHAPTER_ENDED -> {
        val offsetMilliseconds =
          this.exoAdapter.currentTrackOffsetMilliseconds()
        val tocItem =
          this.tocItemFor(this.currentReadingOrderElement.id, offsetMilliseconds)

        this.statusEvents.onNext(
          PlayerEventChapterCompleted(
            readingOrderItem = this.currentReadingOrderElement,
            tocItem = tocItem,
            totalRemainingBookTime = toc.totalDurationRemaining(tocItem, offsetMilliseconds)
          )
        )
        this.playNextSpineElementIfAvailable(this.currentReadingOrderElement, 0L)
      }
    }
  }

  private fun tocItemFor(
    readingOrderID: PlayerManifestReadingOrderID,
    offsetMilliseconds: Long
  ): PlayerManifestTOCItem {
    val toc =
      this.book.tableOfContents
    val item =
      toc.lookupTOCItem(readingOrderID, offsetMilliseconds)
    val element =
      this.book.readingOrderByID[readingOrderID]!!

    return if (item == null) {
      if (element.nextElement == null) {
        return toc.tocItemsInOrder.last()
      }
      toc.tocItemsInOrder.first()
    } else {
      item
    }
  }

  private fun onManifestUpdated() {
    this.statusEvents.onNext(PlayerEvent.PlayerEventManifestUpdated)
  }

  /*
   * A specification of the state the user has asked the player to be in.
   */

  companion object {

    fun create(
      book: ExoAudioBook,
      context: Application,
      manifestUpdates: Observable<Unit>
    ): ExoAudioBookPlayer {
      val statusEvents =
        BehaviorSubject.create<PlayerEvent>()
          .toSerialized()

      return ExoAudioBookPlayer(
        book = book,
        dataSourceFactory = DefaultDataSource.Factory(context),
        exoPlayer = ExoPlayer.Builder(context).build(),
        manifestUpdates = manifestUpdates,
        statusEvents = statusEvents,
      )
    }
  }

  /**
   * The download status of a reading order item has changed. We only actually care about
   * the reading order item that we're either currently playing or are currently waiting for.
   * Everything else is uninteresting.
   */

  private fun onDownloadStatusChanged(status: PlayerReadingOrderItemDownloadStatus) {
    PlayerUIThread.checkIsUIThread()

    when (this.intention) {
      /*
       * If the we're supposed to be playing the current reading order item, and the status is
       * anything other than "downloaded", stop everything. Otherwise, try to play the item.
       */

      SHOULD_BE_PLAYING -> {
        if (this.currentReadingOrderElement.id == status.readingOrderItem.id) {
          when (status) {
            is PlayerReadingOrderItemNotDownloaded,
            is PlayerReadingOrderItemDownloading,
            is PlayerReadingOrderItemDownloadExpired,
            is PlayerReadingOrderItemDownloadFailed -> {
              this.log.debug("Reading order item status changed, stopping playback")
              this.opStop()
            }

            is PlayerReadingOrderItemDownloaded -> {
              this.log.debug("Reading order item status changed, trying to start playback")
              this.moveToReadingOrderItemIfAvailable(
                element = this.currentReadingOrderElement,
                offsetMilliseconds = this.exoAdapter.currentTrackOffsetMilliseconds()
              )
              this.exoAdapter.playIfNotAlreadyPlaying()
            }
          }
        }
      }

      /*
       * If the we're stopped on the current reading order item, and the status is anything other
       * than "downloaded", redundantly stop everything.
       */

      SHOULD_BE_STOPPED -> {
        if (this.currentReadingOrderElement.id == status.readingOrderItem.id) {
          when (status) {
            is PlayerReadingOrderItemNotDownloaded,
            is PlayerReadingOrderItemDownloading,
            is PlayerReadingOrderItemDownloadExpired,
            is PlayerReadingOrderItemDownloadFailed -> {
              this.log.debug("Reading order item status changed, stopping playback")
              this.opStop()
            }

            is PlayerReadingOrderItemDownloaded -> Unit
          }
        }
      }
    }
  }

  /**
   * Configure the current player to use the given playback rate.
   */

  private fun setPlayerPlaybackRate(newRate: PlayerPlaybackRate) {
    this.log.debug("setPlayerPlaybackRate: {}", newRate)

    this.statusEvents.onNext(PlayerEventPlaybackRateChanged(newRate))
    this.exoPlayer.playbackParameters = PlaybackParameters(newRate.speed.toFloat())
  }

  /**
   * Forcefully stop playback and reset the player.
   */

  private fun opStop() {
    this.log.debug("opStop")
    PlayerUIThread.checkIsUIThread()

    this.intention = SHOULD_BE_STOPPED
    this.exoPlayer.stop()
    this.exoPlayer.seekTo(0L)
  }

  private fun playNextSpineElementIfAvailable(
    element: ExoReadingOrderItemHandle,
    offset: Long
  ): SkipChapterStatus {
    this.log.debug("playNextSpineElementIfAvailable: {} {}", element.index, offset)
    PlayerUIThread.checkIsUIThread()

    val next = element.next as ExoReadingOrderItemHandle?
    if (next == null) {
      this.log.debug("reading order item {} has no next element", element.index)
      return SKIP_TO_CHAPTER_NONEXISTENT
    }

    return this.moveToReadingOrderItemIfAvailable(next, offset)
  }

  private fun playPreviousSpineElementIfAvailable(
    element: ExoReadingOrderItemHandle,
    offset: Long
  ): SkipChapterStatus {
    this.log.debug("playPreviousSpineElementIfAvailable: {} {}", element.index, offset)
    PlayerUIThread.checkIsUIThread()

    val previous = element.previous as ExoReadingOrderItemHandle?
    if (previous == null) {
      this.log.debug("reading order item {} has no previous element", element.index)
      return SKIP_TO_CHAPTER_NONEXISTENT
    }

    val newOffset = if (previous.duration != null) {
      previous.duration!!.millis + offset
    } else {
      0L
    }

    return this.moveToReadingOrderItemIfAvailable(previous, newOffset)
  }

  private fun moveToReadingOrderItemIfAvailable(
    element: ExoReadingOrderItemHandle,
    offsetMilliseconds: Long
  ): SkipChapterStatus {
    this.log.debug("moveToReadingOrderItemIfAvailable: {}", element.index)
    PlayerUIThread.checkIsUIThread()

    return when (val downloadStatus = element.downloadStatus) {
      is PlayerReadingOrderItemNotDownloaded,
      is PlayerReadingOrderItemDownloading,
      is PlayerReadingOrderItemDownloadExpired,
      is PlayerReadingOrderItemDownloadFailed -> {
        this.log.debug(
          "moveToReadingOrderItemIfAvailable: reading order item {} is not downloaded ({}), cannot continue",
          element.index, downloadStatus
        )

        this.currentReadingOrderElement = element

        val tocItem =
          tocItemFor(element.id, 0L)
        val toc =
          this.book.tableOfContents

        this.statusEvents.onNext(
          PlayerEventChapterWaiting(
            readingOrderItem = element,
            tocItem = tocItem,
            totalRemainingBookTime = toc.totalDurationRemaining(tocItem, 0L)
          )
        )
        SKIP_TO_CHAPTER_NOT_DOWNLOADED
      }

      is PlayerReadingOrderItemDownloaded -> {
        this.preparePlayer(element, offsetMilliseconds)
        SKIP_TO_CHAPTER_READY
      }
    }
  }

  private fun preparePlayer(
    newElement: ExoReadingOrderItemHandle,
    offset: Long
  ) {
    this.log.debug(
      "preparePlayer: {} (offset {})",
      newElement.index,
      offset
    )

    val uri =
      Uri.fromFile(this.book.downloadTasksByID[newElement.id]!!.partFile)

    this.currentReadingOrderElement = newElement
    this.exoPlayer.setMediaSource(
      ProgressiveMediaSource.Factory(this.dataSourceFactory)
        .createMediaSource(MediaItem.fromUri(uri))
    )

    this.exoPlayer.prepare()
    this.zeroBugTracker.recordTrackDuration(newElement.index, this.exoPlayer.duration)
    this.seek(offset)
  }

  private fun seek(offsetMs: Long) {
    this.log.debug("seek: {}", offsetMs)
    this.exoPlayer.seekTo(offsetMs)
  }

  private fun opSetPlaybackRate(newRate: PlayerPlaybackRate) {
    this.log.debug("opSetPlaybackRate: {}", newRate)
    PlayerUIThread.checkIsUIThread()

    this.currentPlaybackRate = newRate
    this.setPlayerPlaybackRate(newRate)
  }

  private fun opPlay() {
    this.log.debug("opPlay {}", this.intention)
    PlayerUIThread.checkIsUIThread()

    this.intention = SHOULD_BE_PLAYING
    when (this.exoAdapter.state) {
      ExoPlayerPlaybackStatus.INITIAL -> {
        this.preparePlayer(this.currentReadingOrderElement, 0L)
        this.exoPlayer.play()
      }

      ExoPlayerPlaybackStatus.BUFFERING -> {
        this.exoPlayer.play()
      }

      ExoPlayerPlaybackStatus.PLAYING -> {
        // Nothing to do
      }

      ExoPlayerPlaybackStatus.PAUSED -> {
        this.exoPlayer.play()
      }

      ExoPlayerPlaybackStatus.CHAPTER_ENDED -> {
        // Nothing to do
      }
    }
  }

  /**
   * The status of an attempt to switch to a chapter.
   */

  private enum class SkipChapterStatus {

    /**
     * The chapter is not downloaded and therefore cannot be played at the moment.
     */

    SKIP_TO_CHAPTER_NOT_DOWNLOADED,

    /**
     * The chapter does not exist and will never exist.
     */

    SKIP_TO_CHAPTER_NONEXISTENT,

    /**
     * The chapter exists and is ready for playback.
     */

    SKIP_TO_CHAPTER_READY
  }

  private fun opSkipToNextChapter(
    offset: Long
  ): SkipChapterStatus {
    this.log.debug("opSkipToNextChapter")
    PlayerUIThread.checkIsUIThread()
    return this.playNextSpineElementIfAvailable(this.currentReadingOrderElement, offset)
  }

  private fun opSkipToPreviousChapter(
    offset: Long
  ): SkipChapterStatus {
    this.log.debug("opSkipToPreviousChapter")
    PlayerUIThread.checkIsUIThread()
    return this.playPreviousSpineElementIfAvailable(this.currentReadingOrderElement, offset)
  }

  private fun opPause() {
    this.log.debug("opPause")
    PlayerUIThread.checkIsUIThread()

    this.intention = SHOULD_BE_STOPPED
    this.exoPlayer.pause()
  }

  private fun opSkipPlayhead(milliseconds: Long) {
    this.log.debug("opSkipPlayhead")
    PlayerUIThread.checkIsUIThread()

    return when {
      milliseconds == 0L -> {
        // Nothing to do!
      }

      milliseconds > 0 -> {
        this.opSkipForward(milliseconds)
      }

      else -> {
        this.opSkipBack(milliseconds)
      }
    }
  }

  private fun opSkipForward(milliseconds: Long) {
    this.log.debug("opSkipForward")
    PlayerUIThread.checkIsUIThread()

    check(milliseconds > 0) { "Milliseconds must be positive" }
    val nextMs = this.exoPlayer.currentPosition + milliseconds

    if (nextMs > this.exoPlayer.duration) {
      val offset = nextMs - this.exoPlayer.duration
      this.skipToNextChapter(offset = offset)
    } else {
      this.seek(nextMs)
    }

    return when (this.intention) {
      SHOULD_BE_STOPPED -> this.exoPlayer.pause()
      SHOULD_BE_PLAYING -> this.exoAdapter.playIfNotAlreadyPlaying()
    }
  }

  private fun opSkipBack(milliseconds: Long) {
    this.log.debug("opSkipBack")
    PlayerUIThread.checkIsUIThread()

    check(milliseconds < 0) { "Milliseconds must be negative" }
    val nextMs = this.exoPlayer.currentPosition + milliseconds

    if (nextMs < 0) {
      this.skipToPreviousChapter(nextMs)
    } else {
      this.seek(nextMs)
    }

    return when (this.intention) {
      SHOULD_BE_STOPPED -> this.exoPlayer.pause()
      SHOULD_BE_PLAYING -> this.exoAdapter.playIfNotAlreadyPlaying()
    }
  }

  private fun opMovePlayheadToLocation(
    location: PlayerPosition
  ) {
    this.log.debug("opMovePlayheadToLocation: {}", location)
    PlayerUIThread.checkIsUIThread()

    val requestedSpineElement: ExoReadingOrderItemHandle =
      this.book.readingOrderByID[location.readingOrderID]
        ?: return this.log.debug("Reading order item {} does not exist", location)

    /*
     * If the current reading order item is the same as the requested reading order item, then
     * it's more efficient to simply seek to the right offset and start playing.
     */

    if (requestedSpineElement.id == this.currentReadingOrderElement.id) {
      this.seek(location.offsetMilliseconds)
    } else {
      this.moveToReadingOrderItemIfAvailable(
        element = requestedSpineElement,
        offsetMilliseconds = location.offsetMilliseconds
      )
    }
  }

  private fun opClose() {
    this.log.debug("opClose")
    PlayerUIThread.checkIsUIThread()

    this.opStop()
    this.resources.dispose()
  }

  private fun opBookmark() {
    this.log.debug("opBookmark")
    PlayerUIThread.checkIsUIThread()

    val readingOrderItem =
      this.currentReadingOrderElement
    val offsetMilliseconds =
      this.exoAdapter.currentTrackOffsetMilliseconds()
    val tocItem =
      this.tocItemFor(readingOrderItem.id, offsetMilliseconds)
    val durationRemaining =
      this.book.tableOfContents.totalDurationRemaining(tocItem, offsetMilliseconds)

    this.statusEvents.onNext(
      PlayerEvent.PlayerEventWithPosition.PlayerEventCreateBookmark(
        readingOrderItem,
        offsetMilliseconds = offsetMilliseconds,
        tocItem = tocItem,
        totalRemainingBookTime = durationRemaining,
        kind = PlayerBookmarkKind.EXPLICIT
      )
    )
  }

  private fun checkNotClosed() {
    if (this.closed.get()) {
      throw IllegalStateException("Player has been closed")
    }
  }

  override var playbackRate: PlayerPlaybackRate
    get() {
      this.checkNotClosed()
      return this.currentPlaybackRate
    }
    set(value) {
      this.checkNotClosed()
      this.opSetPlaybackRate(value)
    }

  override val events: Observable<PlayerEvent>
    get() {
      this.checkNotClosed()
      return this.statusEvents
    }

  override fun play() {
    this.checkNotClosed()

    runOnUIThread {
      this.opPlay()
    }
  }

  override fun pause() {
    this.checkNotClosed()

    runOnUIThread {
      this.opPause()
    }
  }

  override fun skipToNextChapter(offset: Long) {
    this.checkNotClosed()

    runOnUIThread {
      val status = this.opSkipToNextChapter(offset = offset)

      // if there's no next chapter, the player will go to the end of the chapter
      if (status == SKIP_TO_CHAPTER_NONEXISTENT) {
        this.seek(this.exoPlayer.duration)
      }
    }
  }

  override fun skipToPreviousChapter(offset: Long) {
    this.checkNotClosed()

    runOnUIThread {
      val status = this.opSkipToPreviousChapter(offset = offset)

      // if there's no previous chapter, the player will go to the start of the chapter
      if (status == SKIP_TO_CHAPTER_NONEXISTENT) {
        this.seek(0L)
      }
    }
  }

  override fun skipPlayhead(milliseconds: Long) {
    this.checkNotClosed()

    runOnUIThread {
      this.opSkipPlayhead(milliseconds)
    }
  }

  override fun movePlayheadToLocation(location: PlayerPosition) {
    this.checkNotClosed()
    runOnUIThread {
      this.opMovePlayheadToLocation(location)
    }
  }

  override fun movePlayheadToBookStart() {
    this.checkNotClosed()

    runOnUIThread {
      this.opMovePlayheadToLocation(this.book.readingOrder.first().startingPosition)
    }
  }

  override fun seekTo(milliseconds: Long) {
    this.checkNotClosed()

    runOnUIThread {
      this.seek(milliseconds)
    }
  }

  override fun bookmark() {
    this.checkNotClosed()

    runOnUIThread {
      this.opBookmark()
    }
  }

  override val isClosed: Boolean
    get() = this.closed.get()

  override fun close() {
    if (this.closed.compareAndSet(false, true)) {
      runOnUIThread {
        this.opClose()
      }
    }
  }

  override val playbackStatus: PlayerPlaybackStatus
    get() = when (this.exoAdapter.state) {
      ExoPlayerPlaybackStatus.INITIAL -> PlayerPlaybackStatus.PAUSED
      ExoPlayerPlaybackStatus.BUFFERING -> PlayerPlaybackStatus.BUFFERING
      ExoPlayerPlaybackStatus.PLAYING -> PlayerPlaybackStatus.PLAYING
      ExoPlayerPlaybackStatus.PAUSED -> PlayerPlaybackStatus.PAUSED
      ExoPlayerPlaybackStatus.CHAPTER_ENDED -> PlayerPlaybackStatus.PAUSED
    }

  override val playbackIntention: PlayerPlaybackIntention
    get() = this.intention
}
