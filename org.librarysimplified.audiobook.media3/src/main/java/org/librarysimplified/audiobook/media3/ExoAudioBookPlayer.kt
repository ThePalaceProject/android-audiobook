package org.librarysimplified.audiobook.media3

import android.app.Application
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.datasource.DataSource.Factory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposables
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerBookmarkKind
import org.librarysimplified.audiobook.api.PlayerBookmarkMetadata
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventDeleteBookmark
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventManifestUpdated
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventPlaybackRateChanged
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventChapterCompleted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventChapterWaiting
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventCreateBookmark
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
import org.librarysimplified.audiobook.api.PlayerPositionMetadata
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
import org.librarysimplified.audiobook.media3.ExoAudioBookPlayer.SkipChapterStatus.SKIP_TO_CHAPTER_NONEXISTENT
import org.librarysimplified.audiobook.media3.ExoAudioBookPlayer.SkipChapterStatus.SKIP_TO_CHAPTER_NOT_DOWNLOADED
import org.librarysimplified.audiobook.media3.ExoAudioBookPlayer.SkipChapterStatus.SKIP_TO_CHAPTER_READY
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
  private val dataSourceFactory: Factory,
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

  /**
   * The current intended playback target. This refers to a reading order item and an offset
   * within that item, and essentially represents that most recent place to which the user
   * explicitly navigated (we also count a chapter ending and a new chapter starting as being
   * an explicit navigation).
   */

  private data class CurrentPlaybackTarget(
    val readingOrderItem: ExoReadingOrderItemHandle,
    val readingOrderItemTargetOffsetMilliseconds: Long,
  )

  /**
   * The reading order element that we're currently trying to play.
   */

  @Volatile
  private var currentReadingOrderElement: CurrentPlaybackTarget =
    CurrentPlaybackTarget(
      readingOrderItem = this.book.readingOrder.first(),
      readingOrderItemTargetOffsetMilliseconds = 0L
    )

  /**
   * A flag that indicates whether the current reading order item is being read from local storage,
   * or is being streamed from the network. This flag is expected to be set by the caller when
   * the caller instructs the player to load a new chapter.
   */

  @Volatile
  internal var isStreamingNow: Boolean =
    false

  /**
   * A flag that indicates whether chapters can be streamed before they are downloaded. Users
   * may choose not to permit this various reasons (bandwidth usage, etc).
   */

  @Volatile
  private var isStreamingPermittedField: Boolean =
    false

  /**
   * The state the user has asked the player to be in.
   */

  @Volatile
  private var intention: PlayerPlaybackIntention =
    SHOULD_BE_STOPPED

  private val exoAdapter: ExoAdapter =
    ExoAdapter(
      logger = this.log,
      events = this.statusEvents,
      exoPlayer = this.exoPlayer,
      currentReadingOrderItem = {
        this.currentReadingOrderElement.readingOrderItem
      },
      tocItemFor = { item, time ->
        this.tocItemFor(item.id, time)
      },
      toc = this.book.tableOfContents,
      isStreamingNow = {
        this.isStreamingNow
      }
    )

  init {
    this.resources.add(Disposables.fromAction(this.statusExecutor::shutdown))

    /*
     * Register a bookmark observer.
     */

    this.bookmarkObserver =
      ExoBookmarkObserver.create(
        player = this,
        onBookmarkCreate = this.statusEvents::onNext,
        isStreamingNow = {
          this.isStreamingNow
        }
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
        this.exoAdapter.broadcastPlaybackPosition()
      }
    }, 750L, 750L, TimeUnit.MILLISECONDS)

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

    when (state.newState) {
      ExoPlayerPlaybackStatus.INITIAL -> {
        if (state.oldState == ExoPlayerPlaybackStatus.PLAYING) {
          val offsetMilliseconds =
            this.exoAdapter.currentTrackOffsetMilliseconds()
          val tocItem =
            this.tocItemFor(this.currentReadingOrderElement.readingOrderItem.id, 0L)
          val positionMetadata =
            this.exoAdapter.positionMetadataFor(tocItem, offsetMilliseconds)

          this.statusEvents.onNext(
            PlayerEventPlaybackStopped(
              isStreaming = this.isStreamingNow,
              offsetMilliseconds = 0,
              positionMetadata = positionMetadata,
              readingOrderItem = this.currentReadingOrderElement.readingOrderItem,
            )
          )
        }
      }

      ExoPlayerPlaybackStatus.BUFFERING -> {
        val offsetMilliseconds =
          this.exoAdapter.currentTrackOffsetMilliseconds()
        val tocItem =
          this.tocItemFor(this.currentReadingOrderElement.readingOrderItem.id, offsetMilliseconds)
        val positionMetadata =
          this.exoAdapter.positionMetadataFor(tocItem, offsetMilliseconds)

        this.statusEvents.onNext(
          PlayerEventPlaybackBuffering(
            isStreaming = this.isStreamingNow,
            offsetMilliseconds = offsetMilliseconds,
            positionMetadata = positionMetadata,
            readingOrderItem = this.currentReadingOrderElement.readingOrderItem,
          )
        )
      }

      ExoPlayerPlaybackStatus.PLAYING -> {
        val offsetMilliseconds =
          this.exoAdapter.currentTrackOffsetMilliseconds()
        val tocItem =
          this.tocItemFor(this.currentReadingOrderElement.readingOrderItem.id, offsetMilliseconds)
        val positionMetadata =
          this.exoAdapter.positionMetadataFor(tocItem, offsetMilliseconds)

        if (state.oldState != ExoPlayerPlaybackStatus.PLAYING) {
          this.statusEvents.onNext(
            PlayerEventPlaybackStarted(
              isStreaming = this.isStreamingNow,
              offsetMilliseconds = offsetMilliseconds,
              positionMetadata = positionMetadata,
              readingOrderItem = this.currentReadingOrderElement.readingOrderItem,
            )
          )
        }

        this.statusEvents.onNext(
          PlayerEventPlaybackProgressUpdate(
            isStreaming = this.isStreamingNow,
            offsetMilliseconds = offsetMilliseconds,
            positionMetadata = positionMetadata,
            readingOrderItem = this.currentReadingOrderElement.readingOrderItem,
          )
        )
      }

      ExoPlayerPlaybackStatus.PAUSED -> {
        val offsetMilliseconds =
          this.exoAdapter.currentTrackOffsetMilliseconds()
        val tocItem =
          this.tocItemFor(this.currentReadingOrderElement.readingOrderItem.id, offsetMilliseconds)
        val positionMetadata =
          this.exoAdapter.positionMetadataFor(tocItem, offsetMilliseconds)

        if (this.exoAdapter.isBufferingNow) {
          this.statusEvents.onNext(
            PlayerEventPlaybackBuffering(
              isStreaming = this.isStreamingNow,
              offsetMilliseconds = offsetMilliseconds,
              positionMetadata = positionMetadata,
              readingOrderItem = this.currentReadingOrderElement.readingOrderItem,
            )
          )
        } else {
          this.statusEvents.onNext(
            PlayerEventPlaybackPaused(
              isStreaming = this.isStreamingNow,
              offsetMilliseconds = offsetMilliseconds,
              positionMetadata = positionMetadata,
              readingOrderItem = this.currentReadingOrderElement.readingOrderItem,
            )
          )
        }
      }

      ExoPlayerPlaybackStatus.CHAPTER_ENDED -> {
        val offsetMilliseconds =
          this.exoAdapter.currentTrackOffsetMilliseconds()
        val tocItem =
          this.tocItemFor(this.currentReadingOrderElement.readingOrderItem.id, offsetMilliseconds)
        val positionMetadata =
          this.exoAdapter.positionMetadataFor(tocItem, offsetMilliseconds)

        this.statusEvents.onNext(
          PlayerEventChapterCompleted(
            isStreaming = this.isStreamingNow,
            positionMetadata = positionMetadata,
            readingOrderItem = this.currentReadingOrderElement.readingOrderItem,
          )
        )
        this.playNextSpineElementIfAvailable(this.currentReadingOrderElement.readingOrderItem)
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
    this.statusEvents.onNext(PlayerEventManifestUpdated)
  }

  companion object {

    fun create(
      book: ExoAudioBook,
      context: Application,
      manifestUpdates: Observable<Unit>,
      dataSourceFactory: Factory
    ): ExoAudioBookPlayer {
      val statusEvents =
        BehaviorSubject.create<PlayerEvent>()
          .toSerialized()

      return ExoAudioBookPlayer(
        book = book,
        dataSourceFactory = dataSourceFactory,
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

    /*
     * If the status of the current reading order item becomes "downloaded", and we're
     * intending to play it, then try to start playback.
     */

    when (this.intention) {
      SHOULD_BE_PLAYING -> {
        if (this.currentReadingOrderElement.readingOrderItem.id == status.readingOrderItem.id) {
          when (status) {
            is PlayerReadingOrderItemNotDownloaded,
            is PlayerReadingOrderItemDownloading,
            is PlayerReadingOrderItemDownloadExpired,
            is PlayerReadingOrderItemDownloadFailed -> {
              // Nothing to do.
            }

            is PlayerReadingOrderItemDownloaded -> {
              this.log.debug(
                "Reading order item {} status changed, trying to start playback",
                status.readingOrderItem.id
              )
              this.preparePlayer(this.currentReadingOrderElement)
              this.exoAdapter.playIfNotAlreadyPlaying()
            }
          }
        }
      }

      SHOULD_BE_STOPPED -> {
        // Nothing to do.
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

  private fun playNextSpineElementIfAvailable(element: ExoReadingOrderItemHandle): SkipChapterStatus {
    this.log.debug("playNextSpineElementIfAvailable: {}", element.itemManifest.item.id)
    PlayerUIThread.checkIsUIThread()

    val next = element.next as ExoReadingOrderItemHandle?
    if (next == null) {
      this.log.debug("reading order item {} has no next element", element.index)
      return SKIP_TO_CHAPTER_NONEXISTENT
    }

    return this.preparePlayer(
      CurrentPlaybackTarget(
        readingOrderItem = next,
        readingOrderItemTargetOffsetMilliseconds = 0L
      )
    )
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

    return this.preparePlayer(
      CurrentPlaybackTarget(
        readingOrderItem = previous,
        readingOrderItemTargetOffsetMilliseconds = newOffset
      )
    )
  }

  private fun preparePlayer(target: CurrentPlaybackTarget): SkipChapterStatus {
    this.log.debug(
      "preparePlayer: [{}] (offset {})",
      target.readingOrderItem.id,
      target.readingOrderItemTargetOffsetMilliseconds
    )

    this.currentReadingOrderElement = target

    val tocItem =
      tocItemFor(
        readingOrderID = target.readingOrderItem.id,
        offsetMilliseconds = target.readingOrderItemTargetOffsetMilliseconds)

    val positionMetadata =
      this.exoAdapter.positionMetadataFor(
        tocItem = tocItem,
        readingOrderItemOffsetMilliseconds = target.readingOrderItemTargetOffsetMilliseconds
      )

    val downloadStatus = target.readingOrderItem.downloadStatus
    if (downloadStatus !is PlayerReadingOrderItemDownloaded) {
      if (!this.isStreamingPermitted) {
        this.log.debug(
          "preparePlayer: [{}] Download status is {} and streaming is not permitted. Waiting!",
          target.readingOrderItem.id,
          downloadStatus.javaClass.simpleName
        )

        this.statusEvents.onNext(
          PlayerEventChapterWaiting(
            isStreaming = this.isStreamingNow,
            positionMetadata = positionMetadata,
            readingOrderItem = target.readingOrderItem,
          )
        )
        return SKIP_TO_CHAPTER_NOT_DOWNLOADED
      } else {
        this.log.debug(
          "preparePlayer: [{}] Download status is {} and streaming is permitted. Streaming!",
          target.readingOrderItem.id,
          downloadStatus.javaClass.simpleName
        )
      }
    }

    val partFile =
      this.book.downloadTasksByID[target.readingOrderItem.id]!!.partFile

    val targetURI: Uri =
      if (partFile.isFile) {
        this.isStreamingNow = false
        Uri.fromFile(partFile)
      } else {
        this.isStreamingNow = true
        Uri.parse(target.readingOrderItem.itemManifest.item.link.hrefURI!!.toString())
      }

    this.log.debug(
      "preparePlayer: [{}] Setting media source and preparing player now.",
      target.readingOrderItem.id
    )

    this.exoPlayer.setMediaSource(
      ProgressiveMediaSource.Factory(this.dataSourceFactory)
        .createMediaSource(MediaItem.fromUri(targetURI))
    )

    this.exoPlayer.prepare()
    this.zeroBugTracker.recordTrackDuration(target.readingOrderItem.index, this.exoPlayer.duration)
    this.seek(target.readingOrderItemTargetOffsetMilliseconds)
    return SKIP_TO_CHAPTER_READY
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
        this.preparePlayer(this.currentReadingOrderElement)
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

  private fun opSkipToNextChapter(): SkipChapterStatus {
    this.log.debug("opSkipToNextChapter")
    PlayerUIThread.checkIsUIThread()
    return this.playNextSpineElementIfAvailable(
      this.currentReadingOrderElement.readingOrderItem
    )
  }

  private fun opSkipToPreviousChapter(
    offset: Long
  ): SkipChapterStatus {
    this.log.debug("opSkipToPreviousChapter")
    PlayerUIThread.checkIsUIThread()
    return this.playPreviousSpineElementIfAvailable(
      this.currentReadingOrderElement.readingOrderItem, offset
    )
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

    if (requestedSpineElement.id == this.currentReadingOrderElement.readingOrderItem.id) {
      this.seek(location.offsetMilliseconds)
    } else {
      this.preparePlayer(
        CurrentPlaybackTarget(
          readingOrderItem = requestedSpineElement,
          readingOrderItemTargetOffsetMilliseconds = location.offsetMilliseconds
        )
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
      this.tocItemFor(readingOrderItem.readingOrderItem.id, offsetMilliseconds)
    val durationRemaining =
      this.book.tableOfContents.totalDurationRemaining(tocItem, offsetMilliseconds)

    val bookProgressEstimate =
      readingOrderItem.readingOrderItem.index.toDouble() / this.book.readingOrder.size.toDouble()

    val chapterDuration =
      tocItem.durationMilliseconds
    val chapterOffsetMilliseconds =
      offsetMilliseconds - tocItem.readingOrderOffsetMilliseconds
    val chapterProgressEstimate =
      chapterOffsetMilliseconds.toDouble() / chapterDuration.toDouble()

    val positionMetadata =
      PlayerPositionMetadata(
        tocItem = tocItem,
        totalRemainingBookTime = durationRemaining,
        chapterProgressEstimate = chapterProgressEstimate,
        bookProgressEstimate = bookProgressEstimate
      )

    this.statusEvents.onNext(
      PlayerEventCreateBookmark(
        readingOrderItem.readingOrderItem,
        offsetMilliseconds = offsetMilliseconds,
        kind = PlayerBookmarkKind.EXPLICIT,
        isStreaming = this.isStreamingNow,
        positionMetadata = positionMetadata,
        bookmarkMetadata = PlayerBookmarkMetadata.fromPositionMetadata(positionMetadata)
      )
    )
  }

  private fun opBookmarkDelete(bookmark: PlayerBookmark) {
    this.log.debug("opBookmarkDelete")
    PlayerUIThread.checkIsUIThread()

    this.statusEvents.onNext(PlayerEventDeleteBookmark(bookmark))
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
      val status = this.opSkipToNextChapter()

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

  override fun bookmarkDelete(bookmark: PlayerBookmark) {
    this.checkNotClosed()

    runOnUIThread {
      this.opBookmarkDelete(bookmark)
    }
  }

  override val isClosed: Boolean
    get() = this.closed.get()

  override var isStreamingPermitted: Boolean
    get() =
      this.isStreamingPermittedField
    set(value) {
      this.isStreamingPermittedField = value
    }

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
