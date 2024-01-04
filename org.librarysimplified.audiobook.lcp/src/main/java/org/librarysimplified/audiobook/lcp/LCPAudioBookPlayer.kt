package org.librarysimplified.audiobook.lcp

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.runBlocking
import net.jcip.annotations.GuardedBy
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.librarysimplified.audiobook.api.PlayerBookmark
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
  private val book: LCPAudioBook,
  private val dataSourceFactory: LCPDataSource.Factory,
  private val engineExecutor: ScheduledExecutorService,
  private val exoPlayer: ExoPlayer,
  private val statusEvents: BehaviorSubject<PlayerEvent>,
) : PlayerType {

  companion object {

    private const val TIMEOUT_PLAYER_CREATION = 5L

    fun create(
      book: LCPAudioBook,
      context: Context,
      engineExecutor: ScheduledExecutorService,
      manualPassphrase: Boolean
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

          return@Callable LCPAudioBookPlayer(
            book = book,
            dataSourceFactory = LCPDataSource.Factory(publication),
            engineExecutor = engineExecutor,
            exoPlayer = ExoPlayer.Builder(context).build(),
            statusEvents = statusEvents,
          )
        }
      ).get(
        // if the manual passphrase is enabled, we need to have an infinite timeout, otherwise the
        // creation could be interrupted before the user wrote the passphrase
        if (manualPassphrase) {
          Long.MAX_VALUE
        } else {
          TIMEOUT_PLAYER_CREATION
        }, TimeUnit.SECONDS
      )
    }
  }

  private val closed = AtomicBoolean(false)
  private val bookmarkObserver = ExoBookmarkObserver.create(
    player = this,
    onBookmarkCreate = this.statusEvents::onNext
  )
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

  @Volatile
  private var currentTrackIndex: Int? = null

  private val exoPlayerEventListener = object : Player.Listener {

    override fun onPlayerError(error: PlaybackException) {
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

    override fun onPlaybackStateChanged(playbackState: Int) {
      log.debug(
        "onPlaybackStateChanged: {} ({})", getNameFromPlaybackState(playbackState),
        playbackState
      )
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
      log.debug("onPlayWhenReadyChanged: {} {})", playWhenReady, reason)
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
    hasChapterChangedAfterHandlingLocation(
      location = this.book.spine.first().position,
      playAutomatically = false
    )
  }

  override fun getCurrentPositionAsPlayerBookmark(): PlayerBookmark? {
    this.log.debug("getCurrentPositionAsPlayerBookmark")
    val currentElement = getCurrentSpineElement() ?: return null

    return PlayerBookmark(
      date = DateTime.now().toDateTime(DateTimeZone.UTC),
      position = currentElement.position.copy(
        currentOffset = chapterPlaybackOffset
      ),
      duration = currentElement.duration?.millis ?: 0L,
      uri = currentElement.itemManifest.uri
    )
  }

  override fun movePlayheadToLocation(location: PlayerPosition, playAutomatically: Boolean) {
    this.log.debug("movePlayheadToLocation: {} {}", location, playAutomatically)
    val hasChapterChanged = hasChapterChangedAfterHandlingLocation(
      location = location,
      playAutomatically = playAutomatically
    )

    if (!hasChapterChanged) {
      updateTrackIndex(
        spineElement = spineElementToUpdate!!,
        offset = location.startOffset + location.currentOffset,
        playAutomatically = playAutomatically,
        updateSeek = true
      )
    }
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
  }

  override fun playAtBookStart() {
    this.log.debug("playAtBookStart")
    this.checkNotClosed()
    playAtLocation(this.book.spine.first().position)
  }

  override fun playAtLocation(location: PlayerPosition) {
    this.log.debug("playAtLocation: {}", location)
    this.checkNotClosed()
    val hasChapterChanged = hasChapterChangedAfterHandlingLocation(
      location = location,
      playAutomatically = false
    )

    if (!hasChapterChanged) {
      updateTrackIndex(
        spineElement = spineElementToUpdate!!,
        offset = location.startOffset + location.currentOffset,
        playAutomatically = false,
        updateSeek = true
      )
    }
  }

  override fun skipPlayhead(milliseconds: Long) {
    this.log.debug("skipPlayhead: {}", milliseconds)
    this.checkNotClosed()
    val location = if (milliseconds < 0L || milliseconds > 0L) {
      spineElementToUpdate?.position?.copy(
        currentOffset = chapterPlaybackOffset + milliseconds
      )
    } else {
      null
    }

    location ?: return this.log.debug("there isn't a valid location")

    val currentState = stateGet()
    val playAutomatically = currentState is LCPPlayerState.LCPPlayerStatePlaying
    val hasChapterChanged = hasChapterChangedAfterHandlingLocation(
      location = location,
      playAutomatically = playAutomatically
    )

    if (!hasChapterChanged) {
      updateTrackIndex(
        spineElement = spineElementToUpdate!!,
        offset = location.startOffset + location.currentOffset,
        playAutomatically = playAutomatically,
        updateSeek = true
      )
    }

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

          val location = nextElement.position
          val hasChapterChanged = hasChapterChangedAfterHandlingLocation(
            location = location,
            playAutomatically = true
          )

          if (!hasChapterChanged) {
            updateTrackIndex(
              spineElement = spineElementToUpdate!!,
              offset = location.startOffset + location.currentOffset,
              playAutomatically = true,
              updateSeek = true
            )
          }
        }

        is LCPPlayerState.LCPPlayerStateStopped -> {
          val nextElement = state.spineElement.nextElement as? LCPSpineElement
            ?: return@execute this.log.debug("there's no next chapter")
          val location = nextElement.position
          val hasChapterChanged = hasChapterChangedAfterHandlingLocation(
            location = location,
            playAutomatically = false
          )

          if (!hasChapterChanged) {
            updateTrackIndex(
              spineElement = spineElementToUpdate!!,
              offset = location.startOffset + location.currentOffset,
              playAutomatically = false,
              updateSeek = true
            )
          }
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

          val location = previousElement.position

          val hasChapterChanged = hasChapterChangedAfterHandlingLocation(
            location = location,
            playAutomatically = true
          )

          if (!hasChapterChanged) {
            updateTrackIndex(
              spineElement = spineElementToUpdate!!,
              offset = location.startOffset + location.currentOffset,
              playAutomatically = true,
              updateSeek = true
            )
          }
        }

        is LCPPlayerState.LCPPlayerStateStopped -> {
          val previousElement = state.spineElement.previousElement as? LCPSpineElement
            ?: return@execute this.log.debug("there's no previous chapter")
          val location = previousElement.position

          val hasChapterChanged = hasChapterChangedAfterHandlingLocation(
            location = location,
            playAutomatically = false
          )

          if (!hasChapterChanged) {
            updateTrackIndex(
              spineElement = spineElementToUpdate!!,
              offset = location.startOffset + location.currentOffset,
              playAutomatically = false,
              updateSeek = true
            )
          }
        }
      }
    }
  }

  private fun cancelCurrentObserver() {
    playbackObserver?.cancel(true)
    spineElementToUpdate = null
  }

  private fun getTrackIndexToPlay(
    chapterTrackIndex: Int,
    offset: Long
  ): Int {
    if (chapterTrackIndex < 0 || chapterTrackIndex > tracksToPlay.lastIndex) {
      return -1
    }

    val chapterTrack = tracksToPlay[chapterTrackIndex]
    val trackDuration = chapterTrack.duration

    if (trackDuration == null) {
      this.log.debug("track {} duration is null", chapterTrack)
      return -1
    }

    val trackDurationMillis = trackDuration.toLong() * 1000L

    // the offset is greater than the track duration, so we need to get the next track
    return if (offset > trackDurationMillis) {
      getTrackIndexToPlay(
        chapterTrackIndex = chapterTrackIndex + 1,
        offset = offset - trackDurationMillis
      )
    } else if (offset < 0) {
      if (chapterTrackIndex == 0) {
        -1
      } else {
        val previousTrack = tracksToPlay[chapterTrackIndex - 1]
        val previousTrackDuration = previousTrack.duration?.toLong() ?: 0L

        // the offset is fewer than 0, so we need to get the previous track
        getTrackIndexToPlay(
          chapterTrackIndex = chapterTrackIndex - 1,
          offset = previousTrackDuration + offset
        )
      }
    } else {
      trackPlaybackOffset = offset
      chapterTrackIndex
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

  private fun hasChapterChangedAfterHandlingLocation(
    location: PlayerPosition,
    playAutomatically: Boolean
  ): Boolean {
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

    this.exoPlayer.playbackParameters = PlaybackParameters(newRate.speed.toFloat())
  }

  private fun preparePlayer(playAutomatically: Boolean, newTrackIndex: Int) {
    val trackToPlay = tracksToPlay[newTrackIndex]

    this.log.debug("preparePlayer: {} (offset {})", trackToPlay.title, trackPlaybackOffset)

    val uri = Uri.parse(
      trackToPlay.hrefURI.toString().let {
        if (it.startsWith("/")) it else "/$it"
      }
    )

    this.engineExecutor.execute {
      exoPlayer.setMediaSource(
        ProgressiveMediaSource.Factory(dataSourceFactory)
          .createMediaSource(MediaItem.fromUri(uri))
      )
      exoPlayer.prepare()
      this.seekToTrackPlaybackOffset()
      this.exoPlayer.playWhenReady = playAutomatically
    }
  }

  private fun schedulePlaybackObserverForSpineElement() {
    playbackObserver = this.engineExecutor.scheduleAtFixedRate(
      this.PlaybackObserver(), 1L, 1L, TimeUnit.SECONDS
    )
  }

  private fun seekToTrackPlaybackOffset() {
    this.log.debug("seekTo: {}", trackPlaybackOffset)
    this.engineExecutor.execute { this.exoPlayer.seekTo(trackPlaybackOffset) }
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
        offset = chapter.position.startOffset + chapterPlaybackOffset,
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

  private fun updateTrackIndex(
    spineElement: LCPSpineElement,
    offset: Long,
    playAutomatically: Boolean,
    updateSeek: Boolean
  ) {
    val chapterTrackIndex = tracksToPlay.indexOfFirst { file ->
      spineElement.itemManifest.originalLink.hrefURI == file.hrefURI
    }

    if (chapterTrackIndex == -1) {
      this.log.debug("there's no track to play")
      return
    }

    val newIndex = getTrackIndexToPlay(
      chapterTrackIndex = chapterTrackIndex,
      offset = offset
    )

    if (newIndex == -1) {
      this.log.debug("there's no track to play")
      return
    }

    if (currentTrackIndex != newIndex) {
      currentTrackIndex = newIndex
      preparePlayer(
        playAutomatically = playAutomatically,
        newTrackIndex = newIndex
      )
    }

    if (updateSeek) {
      seekToTrackPlaybackOffset()
    }
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

          bookPlayer.statusEvents.onNext(
            PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackProgressUpdate(
              spineElement = spineElementToUpdate!!,
              offsetMilliseconds = chapterPlaybackOffset
            )
          )

          val location = spineElementToUpdate!!.position.copy(
            currentOffset = chapterPlaybackOffset
          )

          val hasChapterChanged = hasChapterChangedAfterHandlingLocation(
            location = location,
            playAutomatically = true
          )

          if (!hasChapterChanged) {
            updateTrackIndex(
              spineElement = spineElementToUpdate!!,
              offset = location.startOffset + location.currentOffset,
              playAutomatically = true,
              updateSeek = false
            )

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
