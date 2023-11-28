package org.librarysimplified.audiobook.open_access

import android.content.Context
import android.net.Uri
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import net.jcip.annotations.GuardedBy
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventPlaybackRateChanged
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventChapterCompleted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventChapterWaiting
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackPaused
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackProgressUpdate
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackStarted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackStopped
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackWaitingForAction
import org.librarysimplified.audiobook.api.PlayerPlaybackRate
import org.librarysimplified.audiobook.api.PlayerPlaybackRate.NORMAL_TIME
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloadExpired
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloadFailed
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloaded
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloading
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementNotDownloaded
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.open_access.ExoAudioBookPlayer.ExoPlayerState.ExoPlayerStateInitial
import org.librarysimplified.audiobook.open_access.ExoAudioBookPlayer.ExoPlayerState.ExoPlayerStatePlaying
import org.librarysimplified.audiobook.open_access.ExoAudioBookPlayer.ExoPlayerState.ExoPlayerStateStopped
import org.librarysimplified.audiobook.open_access.ExoAudioBookPlayer.ExoPlayerState.ExoPlayerStateWaitingForElement
import org.librarysimplified.audiobook.open_access.ExoAudioBookPlayer.SkipChapterStatus.SKIP_TO_CHAPTER_NONEXISTENT
import org.librarysimplified.audiobook.open_access.ExoAudioBookPlayer.SkipChapterStatus.SKIP_TO_CHAPTER_NOT_DOWNLOADED
import org.librarysimplified.audiobook.open_access.ExoAudioBookPlayer.SkipChapterStatus.SKIP_TO_CHAPTER_READY
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import rx.subjects.BehaviorSubject
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An ExoPlayer player.
 */

class ExoAudioBookPlayer private constructor(
  private val book: ExoAudioBook,
  private val dataSourceFactory: DataSource.Factory,
  private val exoPlayer: ExoPlayer,
  manifestUpdates: Observable<Unit>,
  private val statusEvents: BehaviorSubject<PlayerEvent>
) : PlayerType {

  private val log = LoggerFactory.getLogger(ExoAudioBookPlayer::class.java)

  private val zeroBugTracker: ExoPlayerZeroBugDetection
  private val bookmarkObserver: ExoBookmarkObserver
  private val manifestSubscription: Subscription
  private val closed = AtomicBoolean(false)

  private var shouldPlayAutomatically = false

  init {
    this.manifestSubscription = manifestUpdates.subscribe {
      this.onManifestUpdated()
    }
    this.bookmarkObserver =
      ExoBookmarkObserver.create(
        player = this,
        onBookmarkCreate = this.statusEvents::onNext
      )
    this.zeroBugTracker = ExoPlayerZeroBugDetection(book.spine)
  }

  private fun onManifestUpdated() {
    this.statusEvents.onNext(PlayerEvent.PlayerEventManifestUpdated)
  }

  /*
   * The current playback state.
   */

  private sealed class ExoPlayerState {

    /*
     * The initial state; no spine element is selected, the player is not playing.
     */

    object ExoPlayerStateInitial : ExoPlayerState()

    /*
     * The player is currently playing the given spine element.
     */

    data class ExoPlayerStatePlaying(
      var spineElement: ExoSpineElement,
      val timer: CountDownTimer
    ) :
      ExoPlayerState()

    /*
     * The player is waiting until the given spine element is downloaded before continuing playback.
     */

    data class ExoPlayerStateWaitingForElement(
      var spineElement: ExoSpineElement,
      var offset: Long
    ) :
      ExoPlayerState()

    /*
     * The player was playing the given spine element but is currently paused.
     */

    data class ExoPlayerStateStopped(
      var spineElement: ExoSpineElement
    ) :
      ExoPlayerState()
  }

  @Volatile
  private var currentPlaybackRate: PlayerPlaybackRate = NORMAL_TIME

  @Volatile
  private var currentPlaybackOffset: Long = 0
    set(value) {
      this.log.trace("currentPlaybackOffset: {}", value)
      field = value
    }

  private val stateLock: Any = Object()

  @GuardedBy("stateLock")
  private var state: ExoPlayerState = ExoPlayerStateInitial

  private val downloadEventSubscription: Subscription

  private fun stateSet(state: ExoPlayerState) {
    synchronized(this.stateLock) { this.state = state }
  }

  private fun stateGet(): ExoPlayerState =
    synchronized(this.stateLock) { this.state }

  /*
   * A listener registered with the underlying ExoPlayer instance to observe state changes.
   */

  private val exoPlayerEventListener = object : Player.Listener {
    override fun onPlayerError(error: PlaybackException) {
      log.error("onPlayerError: ", error)
      statusEvents.onNext(
        PlayerEvent.PlayerEventError(
          spineElement = this@ExoAudioBookPlayer.currentSpineElement(),
          exception = Exception(),
          errorCode = -1,
          offsetMilliseconds = this@ExoAudioBookPlayer.currentPlaybackOffset
        )
      )
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
      super.onPlaybackStateChanged(playbackState)
      log.debug(
        "onPlaybackStateChanged: {} ({})", stateName(playbackState),
        playbackState
      )

      if (shouldPlayAutomatically && playbackState == ExoPlayer.STATE_READY) {
        shouldPlayAutomatically = false
        (state as? ExoPlayerStatePlaying)?.timer?.start()
      }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
      super.onIsPlayingChanged(isPlaying)
      log.debug(
        "onIsPlayingChanged: {}", isPlaying
      )
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
      log.debug("onPlayWhenReadyChanged: {} {})", playWhenReady, reason)
    }

    private fun stateName(playbackState: Int): String {
      return when (playbackState) {
        ExoPlayer.STATE_BUFFERING -> "buffering"
        ExoPlayer.STATE_ENDED -> "ended"
        ExoPlayer.STATE_IDLE -> "idle"
        ExoPlayer.STATE_READY -> "ready"
        else -> "unrecognized state"
      }
    }
  }

  init {
    this.exoPlayer.addListener(this.exoPlayerEventListener)

    /*
     * Subscribe to notifications of download changes. This is only needed because the
     * player may be waiting for a chapter to be downloaded and needs to know when it can
     * safely play the chapter.
     */

    this.downloadEventSubscription =
      this.book.spineElementDownloadStatus.subscribe(
        { status -> this.onDownloadStatusChanged(status) },
        { exception -> this.log.error("download status error: ", exception) }
      )
  }

  companion object {

    fun create(
      book: ExoAudioBook,
      context: Context,
      manifestUpdates: Observable<Unit>
    ): ExoAudioBookPlayer {
      val statusEvents =
        BehaviorSubject.create<PlayerEvent>()

      return Executors.newSingleThreadScheduledExecutor().submit(
        Callable {
          return@Callable ExoAudioBookPlayer(
            book = book,
            dataSourceFactory = DefaultDataSource.Factory(context),
            exoPlayer = ExoPlayer.Builder(context).build(),
            manifestUpdates = manifestUpdates,
            statusEvents = statusEvents,
          )
        }
      ).get(5L, SECONDS)
    }
  }

  /**
   * A timer to observe and update the player's state and the UI.
   */

  private inner class ExoAudioBookTimer(
    private val spineElement: ExoSpineElement
  ) : CountDownTimer(Long.MAX_VALUE, 1000L) {
    private var gracePeriod: Int = 1

    override fun onTick(millisUntilFinished: Long) {
      val bookPlayer = this@ExoAudioBookPlayer
      val duration = bookPlayer.exoPlayer.duration
      val position = bookPlayer.exoPlayer.currentPosition
      bookPlayer.log.debug("playback: {}/{}", position, duration)
      this.spineElement.duration = Duration.millis(duration)

      /*
       * Report the current playback status.
       */

      bookPlayer.currentPlaybackOffset = position
      bookPlayer.statusEvents.onNext(
        PlayerEventPlaybackProgressUpdate(this.spineElement, position)
      )

      /*
       * Provide a short grace period before indicating that the current spine element has
       * finished playing. This avoids a situation where the player indicates that it is at
       * the end of the audio but the sound hardware has not actually finished playing the last
       * chunk.
       */

      if (position >= duration) {
        if (this.gracePeriod == 0) {
          bookPlayer.currentPlaybackOffset = bookPlayer.exoPlayer.duration
          bookPlayer.opCurrentTrackFinished()
        }
        --this.gracePeriod
      }
    }

    override fun onFinish() {
      // do nothing
    }
  }

  private fun createPlaybackTimerObserverForSpineElement(
    spineElement: ExoSpineElement
  ): CountDownTimer {
    return ExoAudioBookTimer(spineElement)
  }

  /**
   * The download status of a spine element has changed. We only actually care about
   * the spine element that we're either currently playing or are currently waiting for.
   * Everything else is uninteresting.
   */

  private fun onDownloadStatusChanged(status: PlayerSpineElementDownloadStatus) {
    when (val currentState = this.stateGet()) {
      ExoPlayerStateInitial -> {
        // do nothing
      }

      /*
       * If the we're playing the current spine element, and the status is anything other
       * than "downloaded", stop everything.
       */

      is ExoPlayerStatePlaying -> {
        if (currentState.spineElement == status.spineElement) {
          when (status) {
            is PlayerSpineElementNotDownloaded,
            is PlayerSpineElementDownloading,
            is PlayerSpineElementDownloadExpired,
            is PlayerSpineElementDownloadFailed -> {
              this.log.debug("spine element status changed, stopping playback")
              this.playNothing()
            }

            is PlayerSpineElementDownloaded -> {
              // do nothing
            }
          }
        }
      }

      /*
       * If the we're stopped on the current spine element, and the status is anything other
       * than "downloaded", stop everything.
       */

      is ExoPlayerStateStopped -> {
        if (currentState.spineElement == status.spineElement) {
          when (status) {
            is PlayerSpineElementNotDownloaded,
            is PlayerSpineElementDownloading,
            is PlayerSpineElementDownloadExpired,
            is PlayerSpineElementDownloadFailed -> {
              this.log.debug("spine element status changed, stopping playback")
              this.playNothing()
            }

            is PlayerSpineElementDownloaded -> Unit
          }
        }
      }

      /*
       * If we're waiting for the spine element in question, and the status is now "downloaded",
       * then start playing.
       */

      is ExoPlayerStateWaitingForElement -> {
        if (currentState.spineElement == status.spineElement) {
          when (status) {
            is PlayerSpineElementNotDownloaded,
            is PlayerSpineElementDownloading,
            is PlayerSpineElementDownloadExpired,
            is PlayerSpineElementDownloadFailed -> Unit

            is PlayerSpineElementDownloaded -> {
              this.log.debug("spine element status changed, trying to start playback")
              this.playSpineElementIfAvailable(currentState.spineElement, currentState.offset)
            }
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

  private fun playNothing() {
    this.log.debug("playNothing")

    fun resetPlayer() {
      this.log.debug("playNothing: resetting player")
      this.exoPlayer.stop()
      this.exoPlayer.seekTo(0L)
      this.currentPlaybackOffset = 0
      this.stateSet(ExoPlayerStateInitial)
    }

    return when (val currentState = this.stateGet()) {
      ExoPlayerStateInitial -> resetPlayer()

      is ExoPlayerStatePlaying -> {
        currentState.timer.cancel()
        resetPlayer()
        this.statusEvents.onNext(PlayerEventPlaybackStopped(currentState.spineElement, 0))
      }

      is ExoPlayerStateWaitingForElement -> {
        resetPlayer()
        this.statusEvents.onNext(PlayerEventPlaybackStopped(currentState.spineElement, 0))
      }

      is ExoPlayerStateStopped -> {
        resetPlayer()
        this.statusEvents.onNext(PlayerEventPlaybackStopped(currentState.spineElement, 0))
      }
    }
  }

  private fun playFirstSpineElementIfAvailable(offset: Long): SkipChapterStatus {
    this.log.debug("playFirstSpineElementIfAvailable: {}", offset)

    val firstElement = this.book.spine.firstOrNull()
    if (firstElement == null) {
      this.log.debug("no available initial spine element")
      return SKIP_TO_CHAPTER_NONEXISTENT
    }

    return this.playSpineElementIfAvailable(firstElement, offset)
  }

  private fun playLastSpineElementIfAvailable(offset: Long): SkipChapterStatus {
    this.log.debug("playLastSpineElementIfAvailable: {}", offset)

    val lastElement = this.book.spine.lastOrNull()
    if (lastElement == null) {
      this.log.debug("no available final spine element")
      return SKIP_TO_CHAPTER_NONEXISTENT
    }

    return this.playSpineElementIfAvailable(lastElement, offset)
  }

  private fun playNextSpineElementIfAvailable(
    element: ExoSpineElement,
    offset: Long
  ): SkipChapterStatus {
    this.log.debug("playNextSpineElementIfAvailable: {} {}", element.index, offset)

    val next = element.next as ExoSpineElement?
    if (next == null) {
      this.log.debug("spine element {} has no next element", element.index)
      return SKIP_TO_CHAPTER_NONEXISTENT
    }

    return this.playSpineElementIfAvailable(next, offset, playAutomatically = true)
  }

  private fun playPreviousSpineElementIfAvailable(
    element: ExoSpineElement,
    offset: Long
  ): SkipChapterStatus {
    this.log.debug("playPreviousSpineElementIfAvailable: {} {}", element.index, offset)

    val previous = element.previous as ExoSpineElement?
    if (previous == null) {
      this.log.debug("spine element {} has no previous element", element.index)
      return SKIP_TO_CHAPTER_NONEXISTENT
    }

    val newOffset = if (previous.duration != null) {
      previous.duration!!.millis + offset
    } else {
      0L
    }

    return this.playSpineElementIfAvailable(previous, newOffset, playAutomatically = true)
  }

  private fun playSpineElementIfAvailable(
    element: ExoSpineElement,
    offset: Long,
    playAutomatically: Boolean = false
  ): SkipChapterStatus {
    this.log.debug("playSpineElementIfAvailable: {}", element.index)
    this.playNothing()

    return when (val downloadStatus = element.downloadStatus) {
      is PlayerSpineElementNotDownloaded,
      is PlayerSpineElementDownloading,
      is PlayerSpineElementDownloadExpired,
      is PlayerSpineElementDownloadFailed -> {
        this.log.debug(
          "playSpineElementIfAvailable: spine element {} is not downloaded ({}), cannot continue",
          element.index, downloadStatus
        )

        this.stateSet(ExoPlayerStateWaitingForElement(spineElement = element, offset = offset))
        this.statusEvents.onNext(PlayerEventChapterWaiting(element))
        SKIP_TO_CHAPTER_NOT_DOWNLOADED
      }

      is PlayerSpineElementDownloaded -> {
        this.handleActionOnSpineElement(element, offset, playAutomatically)
        SKIP_TO_CHAPTER_READY
      }
    }
  }

  private fun handleActionOnSpineElement(
    element: ExoSpineElement,
    offset: Long,
    playAutomatically: Boolean
  ) {
    this.log.debug("handling action on element with index: {}", element.index)
    this.log.debug("xxx ${Thread.currentThread().name}")

    shouldPlayAutomatically = playAutomatically
    this.preparePlayer(element, offset, playAutomatically)

    if (playAutomatically) {
      this.stateSet(
        ExoPlayerStatePlaying(
          spineElement = element,
          timer = createPlaybackTimerObserverForSpineElement(element)
        )
      )
      this.statusEvents.onNext(PlayerEventPlaybackStarted(element, offset))
    } else {
      this.stateSet(ExoPlayerStateStopped(element))
      this.statusEvents.onNext(PlayerEventPlaybackWaitingForAction(element, offset))
    }

    this.currentPlaybackOffset = offset
  }

  private fun preparePlayer(
    spineElement: ExoSpineElement,
    offset: Long,
    playAutomatically: Boolean
  ) {
    this.log.debug("preparePlayer: {} (offset {})", spineElement.index, offset)

    val uri =
      Uri.fromFile(book.downloadTasks.first { it.fulfillsSpineElement(spineElement) }.partFile)

    exoPlayer.setMediaSource(
      ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(
        MediaItem.fromUri(uri)
      )
    )

    exoPlayer.prepare()
    zeroBugTracker.recordTrackDuration(spineElement.index, exoPlayer.duration)
    seek(offset)
    exoPlayer.playWhenReady = playAutomatically
  }

  private fun seek(offsetMs: Long) {
    this.log.debug("seek: {}", offsetMs)
    this.exoPlayer.seekTo(offsetMs)
    this.currentPlaybackOffset = offsetMs
  }

  private fun opSetPlaybackRate(newRate: PlayerPlaybackRate) {
    this.log.debug("opSetPlaybackRate: {}", newRate)

    this.currentPlaybackRate = newRate
    this.setPlayerPlaybackRate(newRate)
  }

  private fun opPlay() {
    this.log.debug("opPlay")

    when (val state = this.stateGet()) {
      is ExoPlayerStateInitial -> {
        this.playFirstSpineElementIfAvailable(offset = 0)
      }

      is ExoPlayerStatePlaying ->
        this.log.debug("opPlay: already playing")

      is ExoPlayerStateStopped ->
        this.opPlayStopped(state)

      is ExoPlayerStateWaitingForElement -> {
        this.playSpineElementIfAvailable(state.spineElement, offset = state.offset)
      }
    }
  }

  private fun opPlayStopped(state: ExoPlayerStateStopped) {
    this.log.debug("opPlayStopped")

    this.exoPlayer.playWhenReady = true

    val newState = ExoPlayerStatePlaying(
      spineElement = state.spineElement,
      timer = createPlaybackTimerObserverForSpineElement(
        spineElement = state.spineElement
      )
    )

    this.stateSet(newState)
    newState.timer.start()

    this.statusEvents.onNext(
      PlayerEventPlaybackStarted(
        state.spineElement, this.currentPlaybackOffset
      )
    )
  }

  private fun opCurrentTrackFinished() {
    this.log.debug("opCurrentTrackFinished")

    return when (val state = this.stateGet()) {
      is ExoPlayerStateInitial,
      is ExoPlayerStateWaitingForElement,
      is ExoPlayerStateStopped -> {
        this.log.error("current track is finished but the player thinks it is not playing!")
        throw Unimplemented()
      }

      is ExoPlayerStatePlaying -> {
        this.statusEvents.onNext(PlayerEventChapterCompleted(state.spineElement))

        when (this.playNextSpineElementIfAvailable(state.spineElement, offset = 0)) {
          SKIP_TO_CHAPTER_NOT_DOWNLOADED,
          SKIP_TO_CHAPTER_READY -> Unit

          SKIP_TO_CHAPTER_NONEXISTENT ->
            this.playNothing()
        }
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

  private fun opSkipToNextChapter(offset: Long): SkipChapterStatus {
    this.log.debug("opSkipToNextChapter")

    return when (val state = this.stateGet()) {
      is ExoPlayerStateInitial ->
        this.playFirstSpineElementIfAvailable(offset)

      is ExoPlayerStatePlaying ->
        this.playNextSpineElementIfAvailable(state.spineElement, offset)

      is ExoPlayerStateStopped ->
        this.playNextSpineElementIfAvailable(state.spineElement, offset)

      is ExoPlayerStateWaitingForElement ->
        this.playNextSpineElementIfAvailable(state.spineElement, offset)
    }
  }

  private fun opSkipToPreviousChapter(offset: Long): SkipChapterStatus {
    this.log.debug("opSkipToPreviousChapter")

    return when (val state = this.stateGet()) {
      ExoPlayerStateInitial ->
        this.playLastSpineElementIfAvailable(offset)

      is ExoPlayerStatePlaying ->
        this.playPreviousSpineElementIfAvailable(state.spineElement, offset)

      is ExoPlayerStateWaitingForElement ->
        this.playPreviousSpineElementIfAvailable(state.spineElement, offset)

      is ExoPlayerStateStopped ->
        this.playPreviousSpineElementIfAvailable(state.spineElement, offset)
    }
  }

  private fun opPause() {
    this.log.debug("opPause")

    return when (val state = this.stateGet()) {
      is ExoPlayerStateInitial ->
        this.log.debug("not pausing in the initial state")

      is ExoPlayerStatePlaying ->
        this.opPausePlaying(state)

      is ExoPlayerStateStopped ->
        this.log.debug("not pausing in the stopped state")

      is ExoPlayerStateWaitingForElement ->
        this.log.debug("not pausing in the waiting state")
    }
  }

  private fun opPausePlaying(state: ExoPlayerStatePlaying) {
    this.log.debug("opPausePlaying: offset: {}", this.currentPlaybackOffset)

    state.timer.cancel()
    this.exoPlayer.playWhenReady = false

    this.stateSet(ExoPlayerStateStopped(spineElement = state.spineElement))
    this.statusEvents.onNext(
      PlayerEventPlaybackPaused(
        state.spineElement, this.currentPlaybackOffset
      )
    )
  }

  private fun opSkipPlayhead(milliseconds: Long) {
    this.log.debug("opSkipPlayhead")
    return when {
      milliseconds == 0L -> {
      }

      milliseconds > 0 -> opSkipForward(milliseconds)
      else -> opSkipBack(milliseconds)
    }
  }

  private fun opSkipForward(milliseconds: Long) {
    this.log.debug("opSkipForward")

    assert(milliseconds > 0, { "Milliseconds must be positive" })

    val nextMs = this.exoPlayer.currentPosition + milliseconds

    if (nextMs > this.exoPlayer.duration) {
      val offset = nextMs - this.exoPlayer.duration
      this.skipToNextChapter(
        offset = offset,
      )
    } else {
      this.seek(nextMs)
    }

    return when (val state = this.stateGet()) {
      ExoPlayerStateInitial,
      is ExoPlayerStatePlaying,
      is ExoPlayerStateWaitingForElement -> Unit

      is ExoPlayerStateStopped ->
        this.statusEvents.onNext(
          PlayerEventPlaybackPaused(
            state.spineElement, this.currentPlaybackOffset
          )
        )
    }
  }

  private fun opSkipBack(milliseconds: Long) {
    this.log.debug("opSkipBack")

    assert(milliseconds < 0, { "Milliseconds must be negative" })

    val nextMs = this.exoPlayer.currentPosition + milliseconds

    if (nextMs < 0) {
      this.skipToPreviousChapter(nextMs)
    } else {
      this.seek(nextMs)
    }

    return when (val state = this.stateGet()) {
      ExoPlayerStateInitial,
      is ExoPlayerStatePlaying,
      is ExoPlayerStateWaitingForElement -> Unit

      is ExoPlayerStateStopped ->
        this.statusEvents.onNext(
          PlayerEventPlaybackPaused(
            state.spineElement, this.currentPlaybackOffset
          )
        )
    }
  }

  private fun opPlayAtLocation(location: PlayerPosition, playAutomatically: Boolean = false) {
    this.log.debug("opPlayAtLocation: {}", location)

    val currentSpineElement =
      this.currentSpineElement()

    val requestedSpineElement =
      this.book.spineElementForPartAndChapter(location.part, location.chapter)
        as ExoSpineElement? ?: return this.log.debug("spine element does not exist")

    /*
     * If the current spine element is the same as the requested spine element, then it's more
     * efficient to simply seek to the right offset and start playing.
     */

    if (requestedSpineElement == currentSpineElement) {
      this.seek(location.currentOffset)
      if (playAutomatically) {
        this.opPlay()
      }
    } else {
      this.playSpineElementIfAvailable(
        requestedSpineElement, location.currentOffset,
        playAutomatically
      )
    }
  }

  private fun currentSpineElement(): ExoSpineElement? {
    return when (val state = this.stateGet()) {
      ExoPlayerStateInitial -> null
      is ExoPlayerStatePlaying -> state.spineElement
      is ExoPlayerStateWaitingForElement -> null
      is ExoPlayerStateStopped -> state.spineElement
    }
  }

  private fun opMovePlayheadToLocation(
    location: PlayerPosition,
    playAutomatically: Boolean = false
  ) {
    this.log.debug("opMovePlayheadToLocation: {}", location)
    this.opPlayAtLocation(location, playAutomatically)
    this.opPause()
  }

  private fun opClose() {
    this.log.debug("opClose")
    this.bookmarkObserver.close()
    this.manifestSubscription.unsubscribe()
    this.downloadEventSubscription.unsubscribe()
    this.playNothing()
    this.exoPlayer.release()
    this.statusEvents.onCompleted()
  }

  override val isPlaying: Boolean
    get() {
      this.checkNotClosed()
      return when (this.stateGet()) {
        is ExoPlayerStateInitial -> false
        is ExoPlayerStatePlaying -> true
        is ExoPlayerStateStopped -> false
        is ExoPlayerStateWaitingForElement -> true
      }
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
//      this.engineExecutor2.executeRunnable {
      this.opSetPlaybackRate(value)
//      }
    }

  override val events: Observable<PlayerEvent>
    get() {
      this.checkNotClosed()
      return this.statusEvents
    }

  override fun play() {
    this.checkNotClosed()
    this.opPlay()
  }

  override fun pause() {
    this.checkNotClosed()
    this.opPause()
  }

  override fun skipToNextChapter(offset: Long) {
    this.checkNotClosed()
    val status = this.opSkipToNextChapter(offset = offset)

    // if there's no next chapter, the player will go to the end of the chapter
    if (status == SKIP_TO_CHAPTER_NONEXISTENT) {
      this.seek(this.exoPlayer.duration)
    }
  }

  override fun skipToPreviousChapter(offset: Long) {
    this.checkNotClosed()
    val status = this.opSkipToPreviousChapter(offset = offset)

    // if there's no previous chapter, the player will go to the start of the chapter
    if (status == SKIP_TO_CHAPTER_NONEXISTENT) {
      this.seek(0L)
    }
  }

  override fun skipPlayhead(milliseconds: Long) {
    this.checkNotClosed()
    this.opSkipPlayhead(milliseconds)
  }

  override fun playAtLocation(location: PlayerPosition) {
    this.checkNotClosed()
    this.opPlayAtLocation(location, playAutomatically = true)
  }

  override fun movePlayheadToLocation(location: PlayerPosition, playAutomatically: Boolean) {
    this.checkNotClosed()

    val mainThreadLooper = Looper.getMainLooper()
    Handler(mainThreadLooper).post {
      opMovePlayheadToLocation(location, playAutomatically)
    }
  }

  override fun playAtBookStart() {
    this.checkNotClosed()
    this.opPlayAtLocation(this.book.spine.first().position)
  }

  override fun movePlayheadToBookStart() {
    this.checkNotClosed()
    this.opMovePlayheadToLocation(this.book.spine.first().position)
  }

  override fun getCurrentPositionAsPlayerBookmark(): PlayerBookmark? {
    val currentElement = currentSpineElement() ?: return null

    return PlayerBookmark(
      date = DateTime.now().toDateTime(DateTimeZone.UTC),
      position = currentElement.position.copy(
        currentOffset = currentPlaybackOffset
      ),
      duration = currentElement.duration?.millis ?: 0L,
      uri = currentElement.itemManifest.uri
    )
  }

  override val isClosed: Boolean
    get() = this.closed.get()

  override fun close() {
    if (this.closed.compareAndSet(false, true)) {
      this.opClose()
    }
  }
}
