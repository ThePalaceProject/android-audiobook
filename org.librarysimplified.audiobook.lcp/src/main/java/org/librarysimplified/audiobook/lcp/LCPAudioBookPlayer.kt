package org.librarysimplified.audiobook.lcp

import android.content.Context
import android.media.AudioManager
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import com.google.android.exoplayer.ExoPlaybackException
import com.google.android.exoplayer.ExoPlayer
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer
import com.google.android.exoplayer.MediaCodecSelector
import com.google.android.exoplayer.audio.AudioCapabilities
import com.google.android.exoplayer.extractor.ExtractorSampleSource
import com.google.android.exoplayer.upstream.Allocator
import com.google.android.exoplayer.upstream.DefaultAllocator
import kotlinx.coroutines.runBlocking
import net.jcip.annotations.GuardedBy
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventError
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventPlaybackRateChanged
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventChapterCompleted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackPaused
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackProgressUpdate
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackStarted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackStopped
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackWaitingForAction
import org.librarysimplified.audiobook.api.PlayerPlaybackRate
import org.librarysimplified.audiobook.api.PlayerPlaybackRate.NORMAL_TIME
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.lcp.LCPAudioBookPlayer.LCPPlayerState.LCPPlayerStateInitial
import org.librarysimplified.audiobook.lcp.LCPAudioBookPlayer.LCPPlayerState.LCPPlayerStatePlaying
import org.librarysimplified.audiobook.lcp.LCPAudioBookPlayer.LCPPlayerState.LCPPlayerStateStopped
import org.librarysimplified.audiobook.lcp.LCPAudioBookPlayer.SkipChapterStatus.SKIP_TO_CHAPTER_NONEXISTENT
import org.librarysimplified.audiobook.lcp.LCPAudioBookPlayer.SkipChapterStatus.SKIP_TO_CHAPTER_READY
import org.librarysimplified.audiobook.open_access.ExoBookmarkObserver
import org.librarysimplified.audiobook.open_access.ExoEngineThread
import org.readium.r2.shared.publication.asset.FileAsset
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.logging.ConsoleWarningLogger
import org.readium.r2.streamer.Streamer
import org.readium.r2.streamer.parser.readium.ReadiumWebPubParser
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.BehaviorSubject
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An LCP audio book player.
 */

class LCPAudioBookPlayer private constructor(
  private val engineExecutor: ScheduledExecutorService,
  private val context: Context,
  private val statusEvents: BehaviorSubject<PlayerEvent>,
  private val book: LCPAudioBook,
  private val dataSource: LCPDataSource,
  private val exoPlayer: ExoPlayer,
) : PlayerType {

  private val bookmarkObserver: ExoBookmarkObserver
  private val log = LoggerFactory.getLogger(LCPAudioBookPlayer::class.java)
  private val bufferSegmentSize = 64 * 1024
  private val bufferSegmentCount = 256
  private val closed = AtomicBoolean(false)

  init {
    this.bookmarkObserver =
      ExoBookmarkObserver.create(
        player = this,
        onBookmarkCreate = this.statusEvents::onNext
      )
  }

  /*
   * The current playback state.
   */

  private sealed class LCPPlayerState {

    /*
     * The initial state; no spine element is selected, the player is not playing.
     */

    object LCPPlayerStateInitial : LCPPlayerState()

    /*
     * The player is currently playing the given spine element.
     */

    data class LCPPlayerStatePlaying(
      var spineElement: LCPSpineElement,
      val observerTask: ScheduledFuture<*>
    ) :
      LCPPlayerState()

    /*
     * The player was playing the given spine element but is currently paused.
     */

    data class LCPPlayerStateStopped(
      var spineElement: LCPSpineElement
    ) :
      LCPPlayerState()
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
  private var state: LCPPlayerState = LCPPlayerStateInitial

  private val allocator: Allocator = DefaultAllocator(this.bufferSegmentSize)
  private var exoAudioRenderer: MediaCodecAudioTrackRenderer? = null

  private fun stateSet(state: LCPPlayerState) {
    synchronized(this.stateLock) { this.state = state }
  }

  private fun stateGet(): LCPPlayerState =
    synchronized(this.stateLock) { this.state }

  /*
   * A listener registered with the underlying ExoPlayer instance to observe state changes.
   */

  private val exoPlayerEventListener = object : ExoPlayer.Listener {
    override fun onPlayerError(error: ExoPlaybackException?) {
      this@LCPAudioBookPlayer.log.error("onPlayerError: ", error)
      this@LCPAudioBookPlayer.statusEvents.onNext(
        PlayerEventError(
          spineElement = this@LCPAudioBookPlayer.currentSpineElement(),
          exception = error,
          errorCode = -1,
          offsetMilliseconds = this@LCPAudioBookPlayer.currentPlaybackOffset
        )
      )
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, stateNow: Int) {
      val stateName = this.stateName(stateNow)
      this@LCPAudioBookPlayer.log.debug(
        "onPlayerStateChanged: {} {} ({})", playWhenReady, stateName, stateNow
      )
    }

    private fun stateName(playbackState: Int): String {
      return when (playbackState) {
        ExoPlayer.STATE_BUFFERING -> "buffering"
        ExoPlayer.STATE_ENDED -> "ended"
        ExoPlayer.STATE_IDLE -> "idle"
        ExoPlayer.STATE_PREPARING -> "preparing"
        ExoPlayer.STATE_READY -> "ready"
        else -> "unrecognized state"
      }
    }

    override fun onPlayWhenReadyCommitted() {
      this@LCPAudioBookPlayer.log.debug("onPlayWhenReadyCommitted")
    }
  }

  init {
    this.exoPlayer.addListener(this.exoPlayerEventListener)
  }

  companion object {

    fun create(
      book: LCPAudioBook,
      context: Context,
      engineExecutor: ScheduledExecutorService,
    ): LCPAudioBookPlayer {

      val statusEvents =
        BehaviorSubject.create<PlayerEvent>()

      /*
       * Initialize the audio player on the engine thread.
       */

      return engineExecutor.submit(
        Callable {
          val streamer =
            Streamer(
              context = context,
              parsers = listOf(
                ReadiumWebPubParser(
                  httpClient = DefaultHttpClient(),
                  pdfFactory = null
                )
              ),
              contentProtections = book.contentProtections,
              ignoreDefaultParsers = true
            )

          val publication = runBlocking {
            streamer.open(
              asset = FileAsset(book.file),
              allowUserInteraction = false,
              warnings = ConsoleWarningLogger()
            )
          }.getOrElse {
            throw IOException("Failed to open audio book", it)
          }

          val dataSource = LCPDataSource(publication)

          /*
           * The rendererCount parameter is not well documented. It appears to be the number of
           * renderers that are required to render a single track. To render a piece of video that
           * had video, audio, and a subtitle track would require three renderers. Audio books should
           * require just one.
           */

          val player =
            ExoPlayer.Factory.newInstance(1)

          return@Callable LCPAudioBookPlayer(
            book = book,
            dataSource = dataSource,
            context = context,
            engineExecutor = engineExecutor,
            exoPlayer = player,
            statusEvents = statusEvents,
          )
        }
      ).get(5L, SECONDS)
    }
  }

  /**
   * A playback observer that is called repeatedly to observe the current state of the player.
   */

  private inner class PlaybackObserver(
    private val spineElement: LCPSpineElement
  ) : Runnable {

    private var gracePeriod: Int = 1

    override fun run() {
      val bookPlayer = this@LCPAudioBookPlayer
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
          bookPlayer.engineExecutor.execute {
            bookPlayer.opCurrentTrackFinished()
          }
        }
        --this.gracePeriod
      }
    }
  }

  /**
   * Schedule a playback observer that will check the current state of playback once per second.
   */

  private fun schedulePlaybackObserverForSpineElement(
    spineElement: LCPSpineElement
  ): ScheduledFuture<*> {

    return this.engineExecutor.scheduleAtFixedRate(
      this.PlaybackObserver(spineElement), 1L, 1L, SECONDS
    )
  }

  /**
   * Configure the current player to use the given playback rate.
   */

  private fun setPlayerPlaybackRate(newRate: PlayerPlaybackRate) {
    this.log.debug("setPlayerPlaybackRate: {}", newRate)

    this.statusEvents.onNext(PlayerEventPlaybackRateChanged(newRate))

    /*
     * If the player has not started playing a track, then attempting to set the playback
     * rate on the player will actually end up blocking until another track is loaded.
     */

    if (this.exoAudioRenderer != null) {
      if (Build.VERSION.SDK_INT >= 23) {
        val params = PlaybackParams()
        params.speed = newRate.speed.toFloat()
        this.exoPlayer.sendMessage(this.exoAudioRenderer, 2, params)
      }
    }
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
      this.stateSet(LCPPlayerStateInitial)
    }

    return when (val currentState = this.stateGet()) {
      LCPPlayerStateInitial -> resetPlayer()

      is LCPPlayerStatePlaying -> {
        currentState.observerTask.cancel(true)
        resetPlayer()
        this.statusEvents.onNext(PlayerEventPlaybackStopped(currentState.spineElement, 0))
      }

      is LCPPlayerStateStopped -> {
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

    return this.skipToSpineElement(firstElement, offset)
  }

  private fun playLastSpineElementIfAvailable(offset: Long): SkipChapterStatus {
    this.log.debug("playLastSpineElementIfAvailable: {}", offset)

    val lastElement = this.book.spine.lastOrNull()
    if (lastElement == null) {
      this.log.debug("no available final spine element")
      return SKIP_TO_CHAPTER_NONEXISTENT
    }

    return this.skipToSpineElement(lastElement, offset)
  }

  private fun playNextSpineElementIfAvailable(
    element: LCPSpineElement,
    offset: Long
  ): SkipChapterStatus {
    this.log.debug("playNextSpineElementIfAvailable: {} {}", element.index, offset)

    val next = element.next as LCPSpineElement?
    if (next == null) {
      this.log.debug("spine element {} has no next element", element.index)
      return SKIP_TO_CHAPTER_NONEXISTENT
    }

    return this.skipToSpineElement(next, offset, playAutomatically = true)
  }

  private fun playPreviousSpineElementIfAvailable(
    element: LCPSpineElement,
    offset: Long
  ): SkipChapterStatus {
    this.log.debug("playPreviousSpineElementIfAvailable: {} {}", element.index, offset)

    val previous = element.previous as LCPSpineElement?
    if (previous == null) {
      this.log.debug("spine element {} has no previous element", element.index)
      return SKIP_TO_CHAPTER_NONEXISTENT
    }

    val newOffset = if (previous.duration != null) {
      previous.duration!!.millis + offset
    } else {
      0L
    }

    return this.skipToSpineElement(previous, newOffset, playAutomatically = true)
  }

  private fun skipToSpineElement(
    element: LCPSpineElement,
    offset: Long,
    playAutomatically: Boolean = false
  ): SkipChapterStatus {
    this.log.debug("skipToSpineElement: {}", element.index)
    this.playNothing()
    this.handleActionOnSpineElement(element, offset, playAutomatically)

    return SKIP_TO_CHAPTER_READY
  }

  private fun handleActionOnSpineElement(
    element: LCPSpineElement,
    offset: Long,
    playAutomatically: Boolean
  ) {
    this.log.debug("handling action on element with index: {}", element.index)

    this.preparePlayer(element, offset, playAutomatically)

    if (playAutomatically) {
      this.stateSet(
        LCPPlayerStatePlaying(
          spineElement = element,
          observerTask = this.schedulePlaybackObserverForSpineElement(element)
        )
      )
      this.statusEvents.onNext(PlayerEventPlaybackStarted(element, offset))
    } else {
      this.stateSet(LCPPlayerStateStopped(element))
      this.statusEvents.onNext(PlayerEventPlaybackWaitingForAction(element, offset))
    }

    this.currentPlaybackOffset = offset
  }

  private fun preparePlayer(
    spineElement: LCPSpineElement,
    offset: Long,
    playAutomatically: Boolean)
  {
    this.log.debug("preparePlayer: {} (offset {})", spineElement.index, offset)


    /*
     * Set up an audio renderer for the spine element and tell ExoPlayer to prepare it and then
     * play when ready.
     */

    val uri = Uri.parse(
      spineElement.itemManifest.uri.toString().let {
        if (it.startsWith("/")) it else "/$it"
      }
    )

    val sampleSource =
      ExtractorSampleSource(
        uri,
        this.dataSource,
        this.allocator,
        this.bufferSegmentCount * this.bufferSegmentSize,
        null,
        null,
        0
      )

    this.exoAudioRenderer =
      MediaCodecAudioTrackRenderer(
        sampleSource,
        MediaCodecSelector.DEFAULT,
        null,
        true,
        null,
        null,
        AudioCapabilities.getCapabilities(this.context),
        AudioManager.STREAM_MUSIC
      )

    this.exoPlayer.prepare(this.exoAudioRenderer)
    this.seek(offset)
    this.exoPlayer.playWhenReady = playAutomatically
  }

  private fun seek(offsetMs: Long) {
    this.log.debug("seek: {}", offsetMs)
    this.exoPlayer.seekTo(offsetMs)
    this.currentPlaybackOffset = offsetMs
  }

  private fun opSetPlaybackRate(newRate: PlayerPlaybackRate) {
    ExoEngineThread.checkIsExoEngineThread()
    this.log.debug("opSetPlaybackRate: {}", newRate)

    this.currentPlaybackRate = newRate
    this.setPlayerPlaybackRate(newRate)
  }

  private fun opPlay() {
    ExoEngineThread.checkIsExoEngineThread()
    this.log.debug("opPlay")

    return when (val state = this.stateGet()) {
      is LCPPlayerStateInitial -> {
        this.playFirstSpineElementIfAvailable(offset = 0)
        Unit
      }

      is LCPPlayerStatePlaying ->
        this.log.debug("opPlay: already playing")

      is LCPPlayerStateStopped ->
        this.opPlayStopped(state)
    }
  }

  private fun opPlayStopped(state: LCPPlayerStateStopped) {
    ExoEngineThread.checkIsExoEngineThread()
    this.log.debug("opPlayStopped")

    this.exoPlayer.playWhenReady = true

    this.stateSet(
      LCPPlayerStatePlaying(
        spineElement = state.spineElement,
        observerTask = this.schedulePlaybackObserverForSpineElement(
          spineElement = state.spineElement
        )
      )
    )

    this.statusEvents.onNext(
      PlayerEventPlaybackStarted(
        state.spineElement, this.currentPlaybackOffset
      )
    )
  }

  private fun opCurrentTrackFinished() {
    ExoEngineThread.checkIsExoEngineThread()
    this.log.debug("opCurrentTrackFinished")

    return when (val state = this.stateGet()) {
      is LCPPlayerStateInitial,
      is LCPPlayerStateStopped -> {
        this.log.error("current track is finished but the player thinks it is not playing!")
        throw Unimplemented()
      }

      is LCPPlayerStatePlaying -> {
        this.statusEvents.onNext(PlayerEventChapterCompleted(state.spineElement))

        when (this.playNextSpineElementIfAvailable(state.spineElement, offset = 0)) {
          SKIP_TO_CHAPTER_READY ->
            Unit
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
     * The chapter does not exist and will never exist.
     */

    SKIP_TO_CHAPTER_NONEXISTENT,

    /**
     * The chapter exists and is ready for playback.
     */

    SKIP_TO_CHAPTER_READY
  }

  private fun opSkipToNextChapter(offset: Long): SkipChapterStatus {
    ExoEngineThread.checkIsExoEngineThread()
    this.log.debug("opSkipToNextChapter")

    return when (val state = this.stateGet()) {
      is LCPPlayerStateInitial ->
        this.playFirstSpineElementIfAvailable(offset)
      is LCPPlayerStatePlaying ->
        this.playNextSpineElementIfAvailable(state.spineElement, offset)
      is LCPPlayerStateStopped ->
        this.playNextSpineElementIfAvailable(state.spineElement, offset)
    }
  }

  private fun opSkipToPreviousChapter(offset: Long): SkipChapterStatus {
    ExoEngineThread.checkIsExoEngineThread()
    this.log.debug("opSkipToPreviousChapter")

    return when (val state = this.stateGet()) {
      LCPPlayerStateInitial ->
        this.playLastSpineElementIfAvailable(offset)
      is LCPPlayerStatePlaying ->
        this.playPreviousSpineElementIfAvailable(state.spineElement, offset)
      is LCPPlayerStateStopped ->
        this.playPreviousSpineElementIfAvailable(state.spineElement, offset)
    }
  }

  private fun opPause() {
    ExoEngineThread.checkIsExoEngineThread()
    this.log.debug("opPause")

    return when (val state = this.stateGet()) {
      is LCPPlayerStateInitial ->
        this.log.debug("not pausing in the initial state")
      is LCPPlayerStatePlaying ->
        this.opPausePlaying(state)
      is LCPPlayerStateStopped ->
        this.log.debug("not pausing in the stopped state")
    }
  }

  private fun opPausePlaying(state: LCPPlayerStatePlaying) {
    ExoEngineThread.checkIsExoEngineThread()
    this.log.debug("opPausePlaying: offset: {}", this.currentPlaybackOffset)

    state.observerTask.cancel(true)
    this.exoPlayer.playWhenReady = false

    this.stateSet(LCPPlayerStateStopped(spineElement = state.spineElement))
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
    ExoEngineThread.checkIsExoEngineThread()
    this.log.debug("opSkipForward")

    assert(milliseconds > 0, { "Milliseconds must be positive" })

    val nextMs = this.exoPlayer.currentPosition + milliseconds

    if (nextMs > this.exoPlayer.duration) {
      val offset = nextMs - this.exoPlayer.duration
      this.skipToNextChapter(offset)
    } else {
      this.seek(nextMs)
    }

    return when (val state = this.stateGet()) {
      LCPPlayerStateInitial,
      is LCPPlayerStatePlaying ->
        Unit
      is LCPPlayerStateStopped ->
        this.statusEvents.onNext(
          PlayerEventPlaybackPaused(
            state.spineElement, this.currentPlaybackOffset
          )
        )
    }
  }

  private fun opSkipBack(milliseconds: Long) {
    ExoEngineThread.checkIsExoEngineThread()
    this.log.debug("opSkipBack")

    assert(milliseconds < 0, { "Milliseconds must be negative" })

    val nextMs = this.exoPlayer.currentPosition + milliseconds

    if (nextMs < 0) {
      this.skipToPreviousChapter(nextMs)
    } else {
      this.seek(nextMs)
    }

    return when (val state = this.stateGet()) {
      LCPPlayerStateInitial,
      is LCPPlayerStatePlaying ->
        Unit
      is LCPPlayerStateStopped ->
        this.statusEvents.onNext(
          PlayerEventPlaybackPaused(
            state.spineElement, this.currentPlaybackOffset
          )
        )
    }
  }

  private fun opPlayAtLocation(location: PlayerPosition, playAutomatically: Boolean = false) {
    ExoEngineThread.checkIsExoEngineThread()
    this.log.debug("opPlayAtLocation: {}", location)

    val currentSpineElement =
      this.currentSpineElement()

    val requestedSpineElement =
      this.book.spineElementForPartAndChapter(location.part, location.chapter)
        as LCPSpineElement?

    if (requestedSpineElement == null) {
      return this.log.debug("spine element does not exist")
    }

    /*
     * If the current spine element is the same as the requested spine element, then it's more
     * efficient to simply seek to the right offset and start playing.
     */

    if (requestedSpineElement == currentSpineElement) {
      this.seek(location.offsetMilliseconds)

      if (playAutomatically) {
        this.opPlay()
      }
    } else {
      this.skipToSpineElement(requestedSpineElement, location.offsetMilliseconds, playAutomatically)
    }
  }

  private fun currentSpineElement(): LCPSpineElement? {
    return when (val state = this.stateGet()) {
      LCPPlayerStateInitial -> null
      is LCPPlayerStatePlaying -> state.spineElement
      is LCPPlayerStateStopped -> state.spineElement
    }
  }

  private fun opMovePlayheadToLocation(location: PlayerPosition, playAutomatically: Boolean = false) {
    ExoEngineThread.checkIsExoEngineThread()
    this.log.debug("opMovePlayheadToLocation: {}", location)
    this.opPlayAtLocation(location, playAutomatically)
    this.opPause()
  }

  private fun opClose() {
    ExoEngineThread.checkIsExoEngineThread()
    this.log.debug("opClose")
    this.bookmarkObserver.close()
    this.playNothing()
    this.exoPlayer.release()
    this.statusEvents.onCompleted()
  }

  override val isPlaying: Boolean
    get() {
      this.checkNotClosed()
      return when (this.stateGet()) {
        is LCPPlayerStateInitial -> false
        is LCPPlayerStatePlaying -> true
        is LCPPlayerStateStopped -> false
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
      this.engineExecutor.execute { this.opSetPlaybackRate(value) }
    }

  override val events: Observable<PlayerEvent>
    get() {
      this.checkNotClosed()
      return this.statusEvents
    }

  override fun play() {
    this.checkNotClosed()
    this.engineExecutor.execute { this.opPlay() }
  }

  override fun pause() {
    this.checkNotClosed()
    this.engineExecutor.execute { this.opPause() }
  }

  override fun skipToNextChapter(offset: Long) {
    this.checkNotClosed()
    this.engineExecutor.execute { this.opSkipToNextChapter(offset = offset) }
  }

  override fun skipToPreviousChapter(offset: Long) {
    this.checkNotClosed()
    this.engineExecutor.execute { this.opSkipToPreviousChapter(offset = offset) }
  }

  override fun skipPlayhead(milliseconds: Long) {
    this.checkNotClosed()
    this.engineExecutor.execute { this.opSkipPlayhead(milliseconds) }
  }

  override fun playAtLocation(location: PlayerPosition) {
    this.checkNotClosed()
    this.engineExecutor.execute { this.opPlayAtLocation(location) }
  }

  override fun movePlayheadToLocation(location: PlayerPosition, playAutomatically: Boolean) {
    this.engineExecutor.execute { this.opMovePlayheadToLocation(location, playAutomatically) }
  }

  override fun playAtBookStart() {
    this.checkNotClosed()
    this.engineExecutor.execute { this.opPlayAtLocation(this.book.spine.first().position) }
  }

  override fun movePlayheadToBookStart() {
    this.checkNotClosed()
    this.engineExecutor.execute { this.opMovePlayheadToLocation(this.book.spine.first().position) }
  }

  override val isClosed: Boolean
    get() = this.closed.get()

  override fun close() {
    if (this.closed.compareAndSet(false, true)) {
      this.engineExecutor.execute { this.opClose() }
    }
  }
}
