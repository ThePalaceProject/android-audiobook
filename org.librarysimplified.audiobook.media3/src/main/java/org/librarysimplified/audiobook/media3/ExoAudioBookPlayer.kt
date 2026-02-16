package org.librarysimplified.audiobook.media3

import android.app.Application
import android.net.Uri
import androidx.media3.common.PlaybackParameters
import androidx.media3.datasource.DataSource.Factory
import androidx.media3.exoplayer.ExoPlayer
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposables
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerType
import org.librarysimplified.audiobook.api.PlayerBlame
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
import org.librarysimplified.audiobook.api.PlayerPauseReason
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
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsAbsolute
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsAbsoluteInterval
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsReadingOrderItem
import org.librarysimplified.audiobook.media3.ExoAudioBookPlayer.SkipChapterStatus.SKIP_TO_CHAPTER_NONEXISTENT
import org.librarysimplified.audiobook.media3.ExoAudioBookPlayer.SkipChapterStatus.SKIP_TO_CHAPTER_NOT_DOWNLOADED
import org.librarysimplified.audiobook.media3.ExoAudioBookPlayer.SkipChapterStatus.SKIP_TO_CHAPTER_READY
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * An ExoPlayer player.
 */

class ExoAudioBookPlayer private constructor(
  private val book: ExoAudioBook,
  private val dataSourceFactory: Factory,
  private val exoPlayer: ExoPlayer,
  manifestUpdates: Observable<Unit>,
  private val statusEvents: Subject<PlayerEvent>,
  private val authorizationHandler: PlayerAuthorizationHandlerType
) : PlayerType {

  private var pauseReason: PlayerPauseReason =
    PlayerPauseReason.PAUSE_REASON_INITIALLY_PAUSED

  private val log =
    LoggerFactory.getLogger(ExoAudioBookPlayer::class.java)

  private val closed = AtomicReference<PlayerBlame>()
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
    val readingOrderItemTargetOffsetMilliseconds: PlayerMillisecondsReadingOrderItem,
  )

  /**
   * The reading order element that we're currently trying to play.
   */

  @Volatile
  private var currentReadingOrderElement: CurrentPlaybackTarget =
    CurrentPlaybackTarget(
      readingOrderItem = this.book.readingOrder.first(),
      readingOrderItemTargetOffsetMilliseconds = PlayerMillisecondsReadingOrderItem(0L)
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
    SHOULD_BE_PLAYING

  private val exoAdapter: ExoAdapter =
    ExoAdapter(
      logger = this.log,
      events = this.statusEvents,
      exoPlayer = this.exoPlayer,
      manifest = this.book.manifest,
      currentReadingOrderItem = {
        this.currentReadingOrderElement.readingOrderItem
      },
      authorizationHandler = this.authorizationHandler,
      toc = this.book.tableOfContents,
      isStreamingNow = { this.isStreamingNow }
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
        { exception ->
          this.log.error("Player status error: ", exception)
        }
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
        { exception ->
          this.log.error("Download status error: ", exception)
        }
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
        val offsetMilliseconds: PlayerMillisecondsReadingOrderItem =
          this.exoAdapter.currentTrackOffsetMilliseconds()
        val readingOrderItem =
          this.currentReadingOrderElement.readingOrderItem
        val positionMetadata =
          this.book.tableOfContents.positionMetadataFor(
            readingOrderItemID = readingOrderItem.id,
            readingOrderItemOffset = offsetMilliseconds,
            readingOrderItemInterval = readingOrderItem.interval
          )

        this.statusEvents.onNext(
          PlayerEventPlaybackStopped(
            palaceId = this.book.palaceId,
            isStreaming = this.isStreamingNow,
            readingOrderItemOffsetMilliseconds = offsetMilliseconds,
            positionMetadata = positionMetadata,
            readingOrderItem = this.currentReadingOrderElement.readingOrderItem,
            reason = this.pauseReason
          )
        )
      }

      ExoPlayerPlaybackStatus.BUFFERING -> {
        val offsetMilliseconds: PlayerMillisecondsReadingOrderItem =
          this.exoAdapter.currentTrackOffsetMilliseconds()
        val readingOrderItem =
          this.currentReadingOrderElement.readingOrderItem
        val positionMetadata =
          this.book.tableOfContents.positionMetadataFor(
            readingOrderItemID = readingOrderItem.id,
            readingOrderItemOffset = offsetMilliseconds,
            readingOrderItemInterval = readingOrderItem.interval
          )

        this.statusEvents.onNext(
          PlayerEventPlaybackBuffering(
            palaceId = this.book.palaceId,
            isStreaming = this.isStreamingNow,
            offsetMilliseconds = offsetMilliseconds,
            positionMetadata = positionMetadata,
            readingOrderItem = this.currentReadingOrderElement.readingOrderItem,
          )
        )
      }

      ExoPlayerPlaybackStatus.PLAYING -> {
        val offsetMilliseconds: PlayerMillisecondsReadingOrderItem =
          this.exoAdapter.currentTrackOffsetMilliseconds()
        val readingOrderItem =
          this.currentReadingOrderElement.readingOrderItem
        val positionMetadata =
          this.book.tableOfContents.positionMetadataFor(
            readingOrderItemID = readingOrderItem.id,
            readingOrderItemOffset = offsetMilliseconds,
            readingOrderItemInterval = readingOrderItem.interval
          )

        if (state.oldState != ExoPlayerPlaybackStatus.PLAYING) {
          this.statusEvents.onNext(
            PlayerEventPlaybackStarted(
              palaceId = this.book.palaceId,
              isStreaming = this.isStreamingNow,
              offsetMilliseconds = offsetMilliseconds,
              positionMetadata = positionMetadata,
              readingOrderItem = this.currentReadingOrderElement.readingOrderItem,
            )
          )
        }

        this.statusEvents.onNext(
          PlayerEventPlaybackProgressUpdate(
            palaceId = this.book.palaceId,
            isStreaming = this.isStreamingNow,
            offsetMilliseconds = offsetMilliseconds,
            positionMetadata = positionMetadata,
            readingOrderItem = this.currentReadingOrderElement.readingOrderItem,
          )
        )
      }

      ExoPlayerPlaybackStatus.PAUSED -> {
        val offsetMilliseconds: PlayerMillisecondsReadingOrderItem =
          this.exoAdapter.currentTrackOffsetMilliseconds()
        val readingOrderItem =
          this.currentReadingOrderElement.readingOrderItem
        val positionMetadata =
          this.book.tableOfContents.positionMetadataFor(
            readingOrderItemID = readingOrderItem.id,
            readingOrderItemOffset = offsetMilliseconds,
            readingOrderItemInterval = readingOrderItem.interval
          )

        if (this.exoAdapter.isBufferingNow) {
          this.statusEvents.onNext(
            PlayerEventPlaybackBuffering(
              palaceId = this.book.palaceId,
              isStreaming = this.isStreamingNow,
              offsetMilliseconds = offsetMilliseconds,
              positionMetadata = positionMetadata,
              readingOrderItem = this.currentReadingOrderElement.readingOrderItem,
            )
          )
        } else {
          this.statusEvents.onNext(
            PlayerEventPlaybackPaused(
              palaceId = this.book.palaceId,
              isStreaming = this.isStreamingNow,
              readingOrderItemOffsetMilliseconds = offsetMilliseconds,
              positionMetadata = positionMetadata,
              readingOrderItem = this.currentReadingOrderElement.readingOrderItem,
              reason = this.pauseReason
            )
          )
        }
      }

      ExoPlayerPlaybackStatus.CHAPTER_ENDED -> {
        val offsetMilliseconds: PlayerMillisecondsReadingOrderItem =
          this.exoAdapter.currentTrackOffsetMilliseconds()
        val readingOrderItem =
          this.currentReadingOrderElement.readingOrderItem
        val positionMetadata =
          this.book.tableOfContents.positionMetadataFor(
            readingOrderItemID = readingOrderItem.id,
            readingOrderItemOffset = offsetMilliseconds,
            readingOrderItemInterval = readingOrderItem.interval
          )

        this.statusEvents.onNext(
          PlayerEventChapterCompleted(
            palaceId = this.book.palaceId,
            isStreaming = this.isStreamingNow,
            positionMetadata = positionMetadata,
            readingOrderItem = this.currentReadingOrderElement.readingOrderItem,
          )
        )
        this.playNextSpineElementIfAvailable(this.currentReadingOrderElement.readingOrderItem)
      }
    }
  }

  private fun onManifestUpdated() {
    this.statusEvents.onNext(PlayerEventManifestUpdated(palaceId = this.book.palaceId))
  }

  companion object {

    fun create(
      book: ExoAudioBook,
      context: Application,
      manifestUpdates: Observable<Unit>,
      dataSourceFactory: Factory,
      authorizationHandler: PlayerAuthorizationHandlerType
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
        authorizationHandler = authorizationHandler
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
              if (this.currentReadingOrderElement.readingOrderItem.downloadStatusPrevious !is PlayerReadingOrderItemDownloaded) {
                if (this.exoAdapter.state != ExoPlayerPlaybackStatus.PLAYING) {
                  this.log.debug(
                    "Reading order item {} status changed, and state is {}: Trying to start playback",
                    status.readingOrderItem.id,
                    this.exoAdapter.state
                  )
                  this.preparePlayer(this.currentReadingOrderElement)
                  this.exoAdapter.playIfNotAlreadyPlaying()
                }
              }
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

    this.statusEvents.onNext(
      PlayerEventPlaybackRateChanged(
        palaceId = this.book.palaceId,
        rate = newRate,
      )
    )
    this.exoPlayer.playbackParameters = PlaybackParameters(newRate.speed.toFloat())
  }

  /**
   * Forcefully stop playback and reset the player.
   */

  private fun opStop() {
    this.log.debug("opStop")
    PlayerUIThread.checkIsUIThread()

    this.intention = SHOULD_BE_STOPPED
    this.pauseReason = PlayerPauseReason.PAUSE_REASON_USER_EXPLICITLY_PAUSED
    this.exoPlayer.stop()
    this.exoPlayer.seekTo(0L)
  }

  private fun playNextSpineElementIfAvailable(element: ExoReadingOrderItemHandle): SkipChapterStatus {
    this.log.debug("playNextSpineElementIfAvailable: {}", element.itemManifest.item.id)
    PlayerUIThread.checkIsUIThread()

    val next = element.next
    if (next == null) {
      this.log.debug("reading order item {} has no next element", element.index)
      return SKIP_TO_CHAPTER_NONEXISTENT
    }

    return this.preparePlayer(
      CurrentPlaybackTarget(
        readingOrderItem = next,
        readingOrderItemTargetOffsetMilliseconds = PlayerMillisecondsReadingOrderItem(0L)
      )
    )
  }

  private fun preparePlayer(
    target: CurrentPlaybackTarget
  ): SkipChapterStatus {
    this.log.debug(
      "preparePlayer: [{}] (offset {})",
      target.readingOrderItem.id,
      target.readingOrderItemTargetOffsetMilliseconds
    )

    this.currentReadingOrderElement = target

    val readingOrderItem =
      target.readingOrderItem
    val positionMetadata =
      this.book.tableOfContents.positionMetadataFor(
        readingOrderItemID = readingOrderItem.id,
        readingOrderItemOffset = target.readingOrderItemTargetOffsetMilliseconds,
        readingOrderItemInterval = readingOrderItem.interval
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
            palaceId = this.book.palaceId,
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

    val playbackURI =
      this.book.downloadTasksByID[target.readingOrderItem.id]!!.playbackURI

    val targetURI: Uri =
      when (playbackURI.scheme) {
        null -> {
          this.isStreamingNow = false
          Uri.parse(playbackURI.toString())
        }

        "file" -> {
          this.isStreamingNow = false
          Uri.parse(playbackURI.toString())
        }

        else -> {
          this.isStreamingNow = true
          Uri.parse(playbackURI.toString())
        }
      }

    this.exoAdapter.prepare(
      dataSourceFactory = this.dataSourceFactory,
      targetURI = targetURI,
      offset = target.readingOrderItemTargetOffsetMilliseconds
    )

    this.zeroBugTracker.recordTrackDuration(target.readingOrderItem.index, this.exoPlayer.duration)
    this.seek(target.readingOrderItemTargetOffsetMilliseconds)
    return SKIP_TO_CHAPTER_READY
  }

  private fun seek(offsetMs: PlayerMillisecondsReadingOrderItem) {
    this.log.debug("seek: {}", offsetMs)
    this.exoPlayer.seekTo(offsetMs.value)
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
    this.pauseReason = PlayerPauseReason.PAUSE_REASON_USER_EXPLICITLY_PAUSED

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

  private fun opPause(
    reason: PlayerPauseReason
  ) {
    this.log.debug("opPause {}", reason)
    PlayerUIThread.checkIsUIThread()

    this.intention = SHOULD_BE_STOPPED
    this.pauseReason = reason
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

    /*
     * If the next offset is greater than the current duration, then it means we're trying to seek
     * forwards such that we'd cross a reading order item boundary.
     */

    val nextMs = this.exoPlayer.currentPosition + milliseconds
    if (nextMs > this.exoPlayer.duration) {
      if (!handleFarSeek(milliseconds)) {
        return
      }
    } else {
      this.seek(PlayerMillisecondsReadingOrderItem(nextMs))
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

    /*
     * If the next offset is negative, then it means we're trying to seek backwards such that
     * we'd cross a reading order item boundary. We need to find the reading order item that
     * intersects the new absolute position.
     */

    if (nextMs < 0) {
      if (!this.handleFarSeek(milliseconds)) {
        return
      }
    } else {
      this.seek(PlayerMillisecondsReadingOrderItem(nextMs))
    }

    return when (this.intention) {
      SHOULD_BE_STOPPED -> this.exoPlayer.pause()
      SHOULD_BE_PLAYING -> this.exoAdapter.playIfNotAlreadyPlaying()
    }
  }

  private fun handleFarSeek(
    milliseconds: Long
  ): Boolean {
    /*
     * First, get the current playback position as a position on the absolute timeline.
     */

    val currentReadingOrderItem =
      this.currentReadingOrderElement.readingOrderItem.id
    val currentInterval =
      this.book.tableOfContents.readingOrderIntervals[currentReadingOrderItem]
    if (currentInterval == null) {
      this.log.warn("Cannot find the current reading order item interval!")
      return false
    }
    val currentAbsolutePosition =
      PlayerMillisecondsAbsolute(
        currentInterval.lower.value.plus(this.exoPlayer.currentPosition)
      )

    /*
     * Now add the skip offset to find a new position on the absolute timeline. Then, look up
     * the reading order item that overlaps that new position. If the absolute time is negative,
     * then this means that the user tried to seek backwards to a point _before_ the first reading
     * order item. In this case, we simply snap to the start of the first item in the book.
     */

    val targetAbsoluteMS = currentAbsolutePosition.value + milliseconds
    if (targetAbsoluteMS < 0) {
      val target = this.book.readingOrder.firstOrNull()
      if (target == null) {
        this.log.warn("No first reading order item.")
        return false
      }
      this.preparePlayer(
        CurrentPlaybackTarget(
          readingOrderItem = target,
          readingOrderItemTargetOffsetMilliseconds = PlayerMillisecondsReadingOrderItem(0L)
        )
      )
      return true
    }

    val targetAbsolutePosition =
      PlayerMillisecondsAbsolute(targetAbsoluteMS)
    val overlapping =
      this.book.tableOfContents.readingOrderItemTree.overlapping(
        PlayerMillisecondsAbsoluteInterval(
          targetAbsolutePosition,
          targetAbsolutePosition
        )
      )
    if (overlapping.isEmpty()) {
      this.log.warn("No reading order items overlap the target interval")
      return false
    }
    val targetReadingOrderItemId =
      this.book.tableOfContents.readingOrderItemsByInterval[overlapping.first()]
    if (targetReadingOrderItemId == null) {
      this.log.warn("Unexpectedly failed to locate a reading order item ID.")
      return false
    }
    val targetReadingOrderItem =
      this.book.readingOrderByID[targetReadingOrderItemId]
    if (targetReadingOrderItem == null) {
      this.log.warn("Unexpectedly failed to locate a reading order item.")
      return false
    }

    /*
     * Now, the relative offset into the new reading order item is simply the
     * target absolute time minus the absolute time of the start of the new item.
     */

    val offset =
      PlayerMillisecondsReadingOrderItem(
        targetAbsolutePosition.value.minus(targetReadingOrderItem.interval.lower.value)
      )

    this.preparePlayer(
      CurrentPlaybackTarget(
        readingOrderItem = targetReadingOrderItem,
        readingOrderItemTargetOffsetMilliseconds = offset
      )
    )
    return true
  }

  private fun opMovePlayheadToLocation(
    location: PlayerPosition
  ) {
    this.log.debug("opMovePlayheadToLocation: {}", location)
    PlayerUIThread.checkIsUIThread()

    val requestedSpineElement: ExoReadingOrderItemHandle =
      this.book.readingOrderByID[location.readingOrderID]
        ?: return this.log.debug("Reading order item {} does not exist", location)

    this.preparePlayer(
      CurrentPlaybackTarget(
        readingOrderItem = requestedSpineElement,
        readingOrderItemTargetOffsetMilliseconds = location.offsetMilliseconds
      )
    )
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

    val positionMetadata =
      this.book.tableOfContents.positionMetadataFor(
        readingOrderItemID = readingOrderItem.readingOrderItem.id,
        readingOrderItemOffset = offsetMilliseconds,
        readingOrderItemInterval = readingOrderItem.readingOrderItem.interval
      )

    this.statusEvents.onNext(
      PlayerEventCreateBookmark(
        palaceId = this.book.palaceId,
        readingOrderItem = readingOrderItem.readingOrderItem,
        readingOrderItemOffsetMilliseconds = offsetMilliseconds,
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

    this.statusEvents.onNext(PlayerEventDeleteBookmark(
      palaceId = this.book.palaceId,
      bookmark = bookmark,
    ))
  }

  private fun opMovePlayheadToAbsoluteTime(
    milliseconds: PlayerMillisecondsAbsolute
  ) {
    this.log.debug("opMovePlayheadToAbsoluteTime")

    val readingOrderItems =
      this.book.tableOfContents.readingOrderItemTree.overlapping(
        PlayerMillisecondsAbsoluteInterval(milliseconds, milliseconds)
      )

    if (readingOrderItems.isEmpty()) {
      this.log.warn(
        "opMovePlayheadToAbsoluteTime: No reading order item overlaps absolute time {}ms",
        milliseconds
      )
      return
    }

    val readingOrderItemInterval =
      readingOrderItems.first()
    val readingOrderItemID =
      this.book.tableOfContents.readingOrderItemsByInterval[readingOrderItemInterval]

    if (readingOrderItemID == null) {
      this.log.warn(
        "opMovePlayheadToAbsoluteTime:  No reading order item for interval {}",
        readingOrderItemInterval
      )
      return
    }

    val readingOrderItem =
      this.book.readingOrderByID[readingOrderItemID]

    if (readingOrderItem == null) {
      this.log.warn(
        "opMovePlayheadToAbsoluteTime:  No reading order item for ID {}",
        readingOrderItemID
      )
      return
    }

    val offset =
      PlayerMillisecondsReadingOrderItem((milliseconds - readingOrderItem.interval.lower).value)

    this.preparePlayer(
      CurrentPlaybackTarget(
        readingOrderItem = readingOrderItem,
        readingOrderItemTargetOffsetMilliseconds = offset
      )
    )
  }

  private fun checkNotClosed() {
    PlayerBlame.checkNotClosed(this.closed)
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

  override fun pause(
    reason: PlayerPauseReason
  ) {
    this.checkNotClosed()

    runOnUIThread {
      this.opPause(reason)
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

  override fun movePlayheadToAbsoluteTime(
    milliseconds: PlayerMillisecondsAbsolute
  ) {
    this.checkNotClosed()

    runOnUIThread {
      this.opMovePlayheadToAbsoluteTime(milliseconds)
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
    get() = this.closed.get() != null

  override var isStreamingPermitted: Boolean
    get() =
      this.isStreamingPermittedField
    set(value) {
      this.isStreamingPermittedField = value
    }

  override fun close() {
    if (PlayerBlame.closeIfOpen(this.closed)) {
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
