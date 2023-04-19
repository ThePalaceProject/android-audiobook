package org.librarysimplified.audiobook.lcp

import android.content.Context
import android.media.AudioManager
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import org.librarysimplified.audiobook.api.PlayerPlaybackRate
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerType
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class LCPAudioBookPlayer private constructor(
  private val engineExecutor: ScheduledExecutorService,
  private val context: Context,
  private val statusEvents: BehaviorSubject<PlayerEvent>,
  private val book: LCPAudioBook,
  private val dataSource: LCPDataSource,
  private val exoPlayer: ExoPlayer,
) : PlayerType {

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
      ).get(5L, TimeUnit.SECONDS)
    }
  }

  private val bufferSegmentSize = 64 * 1024
  private val bufferSegmentCount = 256
  private val closed = AtomicBoolean(false)
  private val bookmarkObserver = ExoBookmarkObserver.create(
    player = this,
    onBookmarkCreate = this.statusEvents::onNext
  )
  private val allocator: Allocator = DefaultAllocator(this.bufferSegmentSize)
  private var exoAudioRenderer: MediaCodecAudioTrackRenderer? = null
  private val log = LoggerFactory.getLogger(LCPAudioBookPlayer::class.java)

  @GuardedBy("stateLock")
  private var state: LCPPlayerState = LCPPlayerState.LCPPlayerStateInitial
  private val stateLock: Any = Object()

  private var playbackObserver: ScheduledFuture<*>? = null

  @Volatile
  private var trackPlaybackOffset: Long = 0
    set(value) {
      this.log.trace("trackPlaybackOffset: {}", value)
      field = value
    }


  @Volatile
  private var chapterPlaybackOffset: Long = 0
    set(value) {
      this.log.trace("chapterPlaybackOffset: {}", value)
      field = value
    }

  @Volatile
  private var currentPlaybackRate: PlayerPlaybackRate = PlayerPlaybackRate.NORMAL_TIME

  @Volatile
  private var spineElementToUpdate: LCPSpineElement? = null

  private val tracksToPlay = this.book.spine.map {
    it.itemManifest.originalLink
  }.distinct()

  private var trackIndex = -1

  private val exoPlayerEventListener = object : ExoPlayer.Listener {
    override fun onPlayerError(error: ExoPlaybackException?) {
      log.error("onPlayerError: ", error)
      statusEvents.onNext(
        PlayerEvent.PlayerEventError(
          spineElement = getCurrentSpineElement(),
          exception = error,
          errorCode = -1,
          offsetMilliseconds = chapterPlaybackOffset
        )
      )
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
      log.debug(
        "onPlayerStateChanged: {} {} ({})", playWhenReady, getNameFromPlaybackState(playbackState),
        playbackState
      )
    }

    override fun onPlayWhenReadyCommitted() {
      log.debug("onPlayWhenReadyCommitted")
    }

    private fun getNameFromPlaybackState(playbackState: Int): String {
      return when (playbackState) {
        ExoPlayer.STATE_BUFFERING -> {
          "buffering"
        }
        ExoPlayer.STATE_ENDED -> {
          "ended"
        }
        ExoPlayer.STATE_IDLE -> {
          "idle"
        }
        ExoPlayer.STATE_PREPARING -> {
          "preparing"
        }
        ExoPlayer.STATE_READY -> {
          "ready"
        }
        else -> {
          "unrecognized state"
        }
      }
    }
  }

  init {
    this.exoPlayer.addListener(this.exoPlayerEventListener)
  }

  override val isClosed: Boolean
    get() = this.closed.get()

  override val isPlaying: Boolean
    get() {
      this.checkNotClosed()
      return when (this.stateGet()) {
        is LCPPlayerState.LCPPlayerStateInitial -> false
        is LCPPlayerState.LCPPlayerStatePlaying -> true
        is LCPPlayerState.LCPPlayerStateStopped -> false
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

  override fun close() {
    this.log.debug("close")
    if (this.closed.compareAndSet(false, true)) {
      this.engineExecutor.execute { this.opClose() }
    }
  }

  override fun movePlayheadToBookStart() {
    this.log.debug("movePlayheadToBookStart")
    handleLocationChange(
      location = this.book.spine.first().position,
      playAutomatically = false
    )
  }

  override fun getCurrentSpineElementPositionAndDuration(): Pair<PlayerPosition?, Duration?> {
    this.log.debug("getCurrentSpineElementPositionAndDuration")

    val currentElement = getCurrentSpineElement()
    return currentElement?.position?.copy(currentOffset = chapterPlaybackOffset) to
      currentElement?.duration
  }

  override fun movePlayheadToLocation(location: PlayerPosition, playAutomatically: Boolean) {
    this.log.debug("movePlayheadToLocation: {} {}", location, playAutomatically)
    handleLocationChange(
      location = location,
      playAutomatically = playAutomatically
    )
    updateTrackIndex(
      spineElement = spineElementToUpdate!!,
      trackOffset = location.startOffset + location.currentOffset,
      playAutomatically = playAutomatically,
      updateSeek = true
    )
  }

  override fun pause() {
    this.log.debug("pause")
    this.checkNotClosed()
    this.engineExecutor.execute {
      opPause()
    }
  }

  override fun play() {
    this.log.debug("play")
    this.checkNotClosed()
    this.engineExecutor.execute { this.opPlay() }
    this.exoPlayer.playWhenReady = false
  }

  override fun playAtBookStart() {
    this.log.debug("playAtBookStart")
    this.checkNotClosed()
    playAtLocation(this.book.spine.first().position)
  }

  override fun playAtLocation(location: PlayerPosition) {
    this.log.debug("playAtLocation: {}", location)
    this.checkNotClosed()
    handleLocationChange(
      location = location,
      playAutomatically = false
    )

    updateTrackIndex(
      spineElement = spineElementToUpdate!!,
      trackOffset = location.startOffset + location.currentOffset,
      playAutomatically = false,
      updateSeek = true
    )
  }

  override fun skipPlayhead(milliseconds: Long) {
    this.log.debug("skipPlayhead: {}", milliseconds)
    this.checkNotClosed()
    val location = if (milliseconds < 0L || milliseconds > 0L) {
      val state = stateGet()
      trackPlaybackOffset += milliseconds
      updateTrackIndex(
        spineElement = spineElementToUpdate!!,
        trackOffset = trackPlaybackOffset,
        playAutomatically = state is LCPPlayerState.LCPPlayerStatePlaying,
        updateSeek = true
      )
      spineElementToUpdate?.position?.copy(
        currentOffset = chapterPlaybackOffset + milliseconds
      )
    } else {
      null
    }

    location ?: return this.log.debug("there isn't a valid location")

    val currentState = stateGet()
    handleLocationChange(
      location = location,
      playAutomatically = currentState is LCPPlayerState.LCPPlayerStatePlaying
    )

    when (val state = this.stateGet()) {
      LCPPlayerState.LCPPlayerStateInitial,
      is LCPPlayerState.LCPPlayerStatePlaying -> {
        // do nothing
      }
      is LCPPlayerState.LCPPlayerStateStopped ->
        this.statusEvents.onNext(
          PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackPaused(
            spineElement = state.spineElement,
            offsetMilliseconds = chapterPlaybackOffset
          )
        )
    }
  }

  override fun skipToNextChapter(offset: Long) {
    this.log.debug("skipToNextChapter: {}", offset)
    this.checkNotClosed()
    this.engineExecutor.execute {
      when (val state = this.stateGet()) {
        LCPPlayerState.LCPPlayerStateInitial -> {
          movePlayheadToBookStart()
        }
        is LCPPlayerState.LCPPlayerStatePlaying -> {
          val nextElement = state.spineElement.nextElement as? LCPSpineElement
            ?: return@execute this.log.debug("there's no next chapter")
          handleLocationChange(
            location = nextElement.position,
            playAutomatically = true
          )
        }
        is LCPPlayerState.LCPPlayerStateStopped -> {
          val nextElement = state.spineElement.nextElement as? LCPSpineElement
            ?: return@execute this.log.debug("there's no next chapter")
          handleLocationChange(
            location = nextElement.position,
            playAutomatically = false
          )
        }
      }
    }
  }

  override fun skipToPreviousChapter(offset: Long) {
    this.log.debug("skipToPreviousChapter: {}", offset)
    this.engineExecutor.execute {
      when (val state = this.stateGet()) {
        LCPPlayerState.LCPPlayerStateInitial -> {
          movePlayheadToBookStart()
        }
        is LCPPlayerState.LCPPlayerStatePlaying -> {
          val previousElement = state.spineElement.previousElement as? LCPSpineElement
            ?: return@execute this.log.debug("there's no previous chapter")
          handleLocationChange(
            location = previousElement.position,
            playAutomatically = true
          )
        }
        is LCPPlayerState.LCPPlayerStateStopped -> {
          val previousElement = state.spineElement.previousElement as? LCPSpineElement
            ?: return@execute this.log.debug("there's no previous chapter")
          handleLocationChange(
            location = previousElement.position,
            playAutomatically = false
          )
        }
      }
    }
  }

  private fun cancelCurrentObserver() {
    playbackObserver?.cancel(true)
    spineElementToUpdate = null
  }

  private fun updateTrackIndex(
    spineElement: LCPSpineElement,
    trackOffset: Long,
    playAutomatically: Boolean,
    updateSeek: Boolean
  ) {
    val currentTrackIndex = tracksToPlay.indexOfFirst { file ->
      spineElement.itemManifest.originalLink.hrefURI == file.hrefURI
    }

    if (currentTrackIndex == -1) {
      this.log.debug("there's no track to play")
      return
    }

    val currentTrack = tracksToPlay[currentTrackIndex]

    val trackDuration = currentTrack.duration
      ?: return this.log.debug("track {} duration is null", currentTrack)

    val trackDurationMillis = trackDuration.toLong() * 1000L

    // the offset is greater than the track duration, so we need to get the next track
    if (trackOffset > trackDurationMillis) {
      setNextTrackToPlay(
        index = currentTrackIndex + 1,
        currentOffset = trackOffset - trackDurationMillis
      )
    } else if (trackOffset < 0) {

      // the offset is fewer than 0, so we need to get the previous track
      setPreviousTrackToPlay(
        index = currentTrackIndex - 1,
        currentOffset = trackOffset
      )
    } else {
      trackPlaybackOffset = trackOffset
    }

    if (trackIndex != currentTrackIndex) {
      trackIndex = currentTrackIndex
      preparePlayer(
        playAutomatically = playAutomatically
      )
    }

    if (updateSeek) {
      seekToTrackPlaybackOffset()
    }
  }

  private fun checkNotClosed() {
    if (this.closed.get()) {
      throw IllegalStateException("Player has been closed")
    }
  }

  private fun getCurrentSpineElement(): LCPSpineElement? {
    return when (val state = this.stateGet()) {
      is LCPPlayerState.LCPPlayerStateInitial -> {
        null
      }
      is LCPPlayerState.LCPPlayerStatePlaying -> {
        state.spineElement
      }
      is LCPPlayerState.LCPPlayerStateStopped -> {
        state.spineElement
      }
    }
  }

  private fun getNextElementWithinOffset(
    element: LCPSpineElement?,
    offset: Long
  ): LCPSpineElement? {

    if (element == null) {
      chapterPlaybackOffset = spineElementToUpdate?.duration?.millis ?: Long.MAX_VALUE
      this.log.debug("there's no next element")
      return null
    }

    val elementDuration = element.duration?.millis

    if (elementDuration == null) {
      this.log.debug("the spine element {} duration is null", element)
      return null
    }

    return if (offset >= elementDuration) {
      getNextElementWithinOffset(
        element = element.nextElement as? LCPSpineElement,
        offset = offset - elementDuration
      )
    } else {
      chapterPlaybackOffset = offset
      element
    }
  }

  private fun getPreviousElementWithinOffset(
    element: LCPSpineElement?,
    offset: Long
  ): LCPSpineElement? {
    if (element == null) {
      this.log.debug("there's no previous element")
      chapterPlaybackOffset = 0L
      return null
    }

    val elementDuration = element.duration?.millis

    if (elementDuration == null) {
      this.log.debug("the spine element {} duration is null", element)
      return null
    }

    val positiveOffset = abs(offset)

    return if (positiveOffset > elementDuration) {
      getPreviousElementWithinOffset(
        element = element.previousElement as? LCPSpineElement,
        offset = elementDuration - positiveOffset
      )
    } else {
      chapterPlaybackOffset = elementDuration - positiveOffset
      element
    }
  }

  private fun handleLocationChange(location: PlayerPosition, playAutomatically: Boolean): Boolean {
    val spineElement = this.book.spineElementForPartAndChapter(
      part = location.part,
      chapter = location.chapter
    ) as? LCPSpineElement

    if (spineElement == null) {
      this.log.debug(
        "there's no spine element for part {} and chapter {}", location.part,
        location.chapter
      )
      return false
    }

    val elementDuration = spineElement.duration?.millis

    if (elementDuration == null) {
      this.log.debug("the spine element {} duration is null", spineElement)
      return false
    }

    val currentOffset = location.currentOffset

    val chapter = if (currentOffset >= elementDuration) {
      getNextElementWithinOffset(
        element = spineElement.nextElement as? LCPSpineElement,
        offset = currentOffset - elementDuration
      )
    } else if (currentOffset < 0) {
      getPreviousElementWithinOffset(
        element = spineElement.previousElement as? LCPSpineElement,
        offset = currentOffset
      )
    } else {
      chapterPlaybackOffset = currentOffset
      spineElement
    }

    if (chapter == null) {
      this.log.debug("there's no chapter to play")
      return false
    }

    val hasChapterChanged = spineElementToUpdate != chapter

    // there's not a current element, so we need to create the playback observer
    if (hasChapterChanged) {
      cancelCurrentObserver()
      spineElementToUpdate = chapter
      startNewSchedulerAfterSomeDelay(
        chapter = chapter,
        playAutomatically = playAutomatically
      )
    }

    return hasChapterChanged
  }

  private fun opClose() {
    ExoEngineThread.checkIsExoEngineThread()
    this.log.debug("opClose")
    this.bookmarkObserver.close()
    this.exoPlayer.stop()
    this.exoPlayer.release()
    this.statusEvents.onCompleted()
    this.playbackObserver?.cancel(true)
  }

  private fun opPause() {
    when (val state = this.stateGet()) {
      LCPPlayerState.LCPPlayerStateInitial -> {
        this.log.debug("not pausing in the initial state")
      }
      is LCPPlayerState.LCPPlayerStatePlaying -> {
        this.log.debug(
          "pausing with trackOffset: {} and chapterOffset: {}", trackPlaybackOffset,
          chapterPlaybackOffset
        )

        this.exoPlayer.playWhenReady = false

        this.stateSet(LCPPlayerState.LCPPlayerStateStopped(spineElement = state.spineElement))
        this.statusEvents.onNext(
          PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackPaused(
            spineElement = state.spineElement,
            offsetMilliseconds = chapterPlaybackOffset
          )
        )
      }
      is LCPPlayerState.LCPPlayerStateStopped -> {
        this.log.debug("not pausing in the stopped state")
      }
    }
  }

  private fun opPlay() {
    ExoEngineThread.checkIsExoEngineThread()
    this.log.debug("opPlay")

    when (val state = this.stateGet()) {
      LCPPlayerState.LCPPlayerStateInitial -> {
        movePlayheadToLocation(
          location = this.book.spine.first().position,
          playAutomatically = true
        )
      }
      is LCPPlayerState.LCPPlayerStatePlaying -> {
        this.log.debug("opPlay: already playing")
      }
      is LCPPlayerState.LCPPlayerStateStopped -> {
        this.log.debug("opPlay: play on stopped state")

        this.exoPlayer.playWhenReady = true

        this.stateSet(
          LCPPlayerState.LCPPlayerStatePlaying(
            spineElement = state.spineElement
          )
        )

        this.statusEvents.onNext(
          PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackStarted(
            spineElement = state.spineElement,
            offsetMilliseconds = chapterPlaybackOffset
          )
        )
      }
    }
  }

  private fun opSetPlaybackRate(newRate: PlayerPlaybackRate) {
    ExoEngineThread.checkIsExoEngineThread()
    this.log.debug("opSetPlaybackRate: {}", newRate)

    this.currentPlaybackRate = newRate

    this.statusEvents.onNext(PlayerEvent.PlayerEventPlaybackRateChanged(newRate))

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

  private fun preparePlayer(playAutomatically: Boolean) {
    val trackToPlay = tracksToPlay[trackIndex]

    this.log.debug("preparePlayer: {} (offset {})", trackToPlay.title, trackPlaybackOffset)

    val uri = Uri.parse(
      trackToPlay.hrefURI.toString().let {
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
    this.seekToTrackPlaybackOffset()
    this.exoPlayer.playWhenReady = playAutomatically
  }

  private fun schedulePlaybackObserverForSpineElement() {
    playbackObserver = this.engineExecutor.scheduleAtFixedRate(
      this.PlaybackObserver(), 1L, 1L, TimeUnit.SECONDS
    )
  }

  private fun seekToTrackPlaybackOffset() {
    this.log.debug("seekTo: {}", trackPlaybackOffset)
    this.exoPlayer.seekTo(trackPlaybackOffset)
  }

  private fun setNextTrackToPlay(index: Int, currentOffset: Long) {
    if (index > tracksToPlay.lastIndex) {
      trackPlaybackOffset = this.exoPlayer.duration
      seekToTrackPlaybackOffset()
      this.log.debug("there's no next track")
      return
    }

    val nextTrack = tracksToPlay[index]
    val trackDuration = nextTrack.duration ?: return
    val trackDurationMillis = trackDuration.toLong() * 1000L

    return if (currentOffset > trackDurationMillis) {
      setNextTrackToPlay(
        index = index + 1,
        currentOffset = currentOffset - trackDurationMillis
      )
    } else {
      trackIndex = index
      trackPlaybackOffset = currentOffset
    }
  }

  private fun setPreviousTrackToPlay(index: Int, currentOffset: Long) {
    if (index < 0) {
      trackPlaybackOffset = 0L
      seekToTrackPlaybackOffset()
      this.log.debug("there's no previous track")
      return
    }

    val positiveOffset = abs(currentOffset)
    val previousTrack = tracksToPlay[index]
    val trackDuration = previousTrack.duration ?: return
    val trackDurationMillis = trackDuration.toLong() * 1000L

    if (trackDurationMillis - positiveOffset < 0) {
      setPreviousTrackToPlay(
        index = index - 1,
        currentOffset = trackDurationMillis - positiveOffset
      )
    } else {
      trackIndex = index
      trackPlaybackOffset = trackDurationMillis - positiveOffset
    }
  }

  private fun startNewSchedulerAfterSomeDelay(
    chapter: LCPSpineElement,
    playAutomatically: Boolean
  ) {

    // we are starting a new scheduler after some small delay so the UI can be updated with the last
    // offset values
    Handler(Looper.getMainLooper()).postDelayed({

      updateTrackIndex(
        spineElement = chapter,
        trackOffset = chapter.position.startOffset + chapterPlaybackOffset,
        playAutomatically = playAutomatically,
        updateSeek = true
      )

      schedulePlaybackObserverForSpineElement()

      if (playAutomatically) {
        this.stateSet(
          LCPPlayerState.LCPPlayerStatePlaying(
            spineElement = chapter
          )
        )
        this.statusEvents.onNext(
          PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackStarted(
            spineElement = chapter,
            offsetMilliseconds = chapterPlaybackOffset
          )
        )
      } else {
        this.stateSet(
          LCPPlayerState.LCPPlayerStateStopped(
            spineElement = chapter
          )
        )
        this.statusEvents.onNext(
          PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackWaitingForAction(
            spineElement = chapter,
            offsetMilliseconds = chapterPlaybackOffset
          )
        )
      }
    }, 250L)
  }

  private fun stateGet(): LCPPlayerState {
    return synchronized(this.stateLock) { this.state }
  }

  private fun stateSet(state: LCPPlayerState) {
    synchronized(this.stateLock) { this.state = state }
  }

  private inner class PlaybackObserver : Runnable {
    override fun run() {
      when (stateGet()) {
        is LCPPlayerState.LCPPlayerStateInitial,
        is LCPPlayerState.LCPPlayerStateStopped -> {
          // do nothing
        }
        is LCPPlayerState.LCPPlayerStatePlaying -> {
          spineElementToUpdate ?: return

          val bookPlayer = this@LCPAudioBookPlayer
          trackPlaybackOffset = bookPlayer.exoPlayer.currentPosition
          updateTrackIndex(
            spineElement = spineElementToUpdate!!,
            trackOffset = trackPlaybackOffset,
            playAutomatically = true,
            updateSeek = false
          )

          bookPlayer.statusEvents.onNext(
            PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackProgressUpdate(
              spineElement = spineElementToUpdate!!,
              offsetMilliseconds = chapterPlaybackOffset
            )
          )

          val hasChapterChanged = handleLocationChange(
            location = spineElementToUpdate!!.position.copy(
              currentOffset = chapterPlaybackOffset
            ),
            playAutomatically = true
          )
          if (!hasChapterChanged) {
            chapterPlaybackOffset += 1000L
          }
        }
      }
    }
  }

  private sealed class LCPPlayerState {

    /*
     * The initial state; no spine element is selected, the player is not playing.
     */

    object LCPPlayerStateInitial : LCPPlayerState()

    /*
     * The player is currently playing the given spine element.
     */

    data class LCPPlayerStatePlaying(
      var spineElement: LCPSpineElement
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
}
