package org.librarysimplified.audiobook.lcp

import android.app.Application
import android.net.Uri
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
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.librarysimplified.audiobook.open_access.ExoBookmarkObserver
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.ErrorException
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.BehaviorSubject
import java.util.concurrent.CompletableFuture
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
      context: Application,
      engineExecutor: ScheduledExecutorService,
      manualPassphrase: Boolean
    ): LCPAudioBookPlayer {
      val statusEvents =
        BehaviorSubject.create<PlayerEvent>()

      /*
       * The Media3 audio player now has the restriction that it must be created on the UI thread.
       */

      val exoFuture = CompletableFuture<ExoPlayer>()
      PlayerUIThread.runOnUIThread {
        exoFuture.complete(ExoPlayer.Builder(context).build())
      }
      val exoPlayer =
        exoFuture.get(TIMEOUT_PLAYER_CREATION, TimeUnit.SECONDS)
      val publication =
        openPublication(context, book)

      return LCPAudioBookPlayer(
        book = book,
        dataSourceFactory = LCPDataSource.Factory(publication),
        engineExecutor = engineExecutor,
        exoPlayer = exoPlayer,
        statusEvents = statusEvents,
      )
    }

    private fun openPublication(
      context: Application,
      book: LCPAudioBook
    ): Publication {
      return runBlocking {
        val httpClient =
          DefaultHttpClient()
        val assetRetriever =
          AssetRetriever(context.contentResolver, httpClient)

        when (val assetR = assetRetriever.retrieve(book.file)) {
          is Try.Failure -> throw ErrorException(assetR.value)
          is Try.Success -> {
            val publicationParser =
              DefaultPublicationParser(
                context = context,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                pdfFactory = LCPNoPDFFactory,
              )
            val publicationOpener =
              PublicationOpener(
                publicationParser = publicationParser,
                contentProtections = book.contentProtections,
                onCreatePublication = {
                },
              )

            when (val pubR = publicationOpener.open(
              asset = assetR.value,
              credentials = null,
              allowUserInteraction = false,
            )) {
              is Try.Failure -> throw ErrorException(pubR.value)
              is Try.Success -> pubR.value
            }
          }
        }
      }
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
      this@LCPAudioBookPlayer.log.error("onPlayerError: ", error)
      this@LCPAudioBookPlayer.statusEvents.onNext(
        PlayerEvent.PlayerEventError(
          spineElement = this@LCPAudioBookPlayer.getCurrentSpineElement(),
          exception = error,
          errorCode = -1,
          offsetMilliseconds = this@LCPAudioBookPlayer.chapterPlaybackOffset
        )
      )
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
      this@LCPAudioBookPlayer.log.debug(
        "onPlaybackStateChanged: {} ({})", this.getNameFromPlaybackState(playbackState),
        playbackState
      )
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
      this@LCPAudioBookPlayer.log.debug("onPlayWhenReadyChanged: {} {})", playWhenReady, reason)
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
      PlayerUIThread.runOnUIThread { this.opSetPlaybackRate(value) }
    }

  override val events: Observable<PlayerEvent>
    get() {
      this.checkNotClosed()
      return this.statusEvents
    }

  override fun close() {
    this.log.debug("close")
    if (this.closed.compareAndSet(false, true)) {
      PlayerUIThread.runOnUIThread {
        this.opClose()
      }
    }
  }

  override fun movePlayheadToBookStart() {
    this.log.debug("movePlayheadToBookStart")
    this.hasChapterChangedAfterHandlingLocation(
      location = this.book.spine.first().position,
      playAutomatically = false
    )
  }

  override fun getCurrentPositionAsPlayerBookmark(): PlayerBookmark? {
    this.log.debug("getCurrentPositionAsPlayerBookmark")
    val currentElement = this.getCurrentSpineElement() ?: return null

    return PlayerBookmark(
      date = DateTime.now().toDateTime(DateTimeZone.UTC),
      position = currentElement.position.copy(
        currentOffset = this.chapterPlaybackOffset
      ),
      duration = currentElement.duration?.millis ?: 0L,
      uri = currentElement.itemManifest.uri
    )
  }

  override fun movePlayheadToLocation(location: PlayerPosition, playAutomatically: Boolean) {
    this.log.debug("movePlayheadToLocation: {} {}", location, playAutomatically)
    val hasChapterChanged = this.hasChapterChangedAfterHandlingLocation(
      location = location,
      playAutomatically = playAutomatically
    )

    if (!hasChapterChanged) {
      this.updateTrackIndex(
        spineElement = this.spineElementToUpdate!!,
        offset = location.startOffset + location.currentOffset,
        playAutomatically = playAutomatically,
        updateSeek = true
      )
    }
  }

  override fun pause() {
    this.log.debug("pause")
    this.checkNotClosed()
    PlayerUIThread.runOnUIThread {
      this.opPause()
    }
  }

  override fun play() {
    this.log.debug("play")
    this.checkNotClosed()
    PlayerUIThread.runOnUIThread {
      this.opPlay()
    }
  }

  override fun playAtBookStart() {
    this.log.debug("playAtBookStart")
    this.checkNotClosed()
    this.playAtLocation(this.book.spine.first().position)
  }

  override fun playAtLocation(location: PlayerPosition) {
    this.log.debug("playAtLocation: {}", location)
    this.checkNotClosed()
    val hasChapterChanged = this.hasChapterChangedAfterHandlingLocation(
      location = location,
      playAutomatically = false
    )

    if (!hasChapterChanged) {
      this.updateTrackIndex(
        spineElement = this.spineElementToUpdate!!,
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
      this.spineElementToUpdate?.position?.copy(
        currentOffset = this.chapterPlaybackOffset + milliseconds
      )
    } else {
      null
    }

    location ?: return this.log.debug("there isn't a valid location")

    val currentState = this.stateGet()
    val playAutomatically = currentState is LCPPlayerState.LCPPlayerStatePlaying
    val hasChapterChanged = this.hasChapterChangedAfterHandlingLocation(
      location = location,
      playAutomatically = playAutomatically
    )

    if (!hasChapterChanged) {
      this.updateTrackIndex(
        spineElement = this.spineElementToUpdate!!,
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
            offsetMilliseconds = this.chapterPlaybackOffset
          )
        )
    }
  }

  override fun skipToNextChapter(offset: Long) {
    this.log.debug("skipToNextChapter: {}", offset)
    this.checkNotClosed()
    PlayerUIThread.runOnUIThread {
      when (val state = this.stateGet()) {
        LCPPlayerState.LCPPlayerStateInitial -> {
          this.movePlayheadToBookStart()
        }

        is LCPPlayerState.LCPPlayerStatePlaying -> {
          val nextElement = state.spineElement.nextElement as? LCPSpineElement
            ?: return@runOnUIThread this.log.debug("there's no next chapter")

          val location = nextElement.position
          val hasChapterChanged = this.hasChapterChangedAfterHandlingLocation(
            location = location,
            playAutomatically = true
          )

          if (!hasChapterChanged) {
            this.updateTrackIndex(
              spineElement = this.spineElementToUpdate!!,
              offset = location.startOffset + location.currentOffset,
              playAutomatically = true,
              updateSeek = true
            )
          }
        }

        is LCPPlayerState.LCPPlayerStateStopped -> {
          val nextElement = state.spineElement.nextElement as? LCPSpineElement
            ?: return@runOnUIThread this.log.debug("there's no next chapter")
          val location = nextElement.position
          val hasChapterChanged = this.hasChapterChangedAfterHandlingLocation(
            location = location,
            playAutomatically = false
          )

          if (!hasChapterChanged) {
            this.updateTrackIndex(
              spineElement = this.spineElementToUpdate!!,
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
    PlayerUIThread.runOnUIThread {
      when (val state = this.stateGet()) {
        LCPPlayerState.LCPPlayerStateInitial -> {
          this.movePlayheadToBookStart()
        }

        is LCPPlayerState.LCPPlayerStatePlaying -> {
          val previousElement = state.spineElement.previousElement as? LCPSpineElement
            ?: return@runOnUIThread this.log.debug("there's no previous chapter")

          val location = previousElement.position

          val hasChapterChanged = this.hasChapterChangedAfterHandlingLocation(
            location = location,
            playAutomatically = true
          )

          if (!hasChapterChanged) {
            this.updateTrackIndex(
              spineElement = this.spineElementToUpdate!!,
              offset = location.startOffset + location.currentOffset,
              playAutomatically = true,
              updateSeek = true
            )
          }
        }

        is LCPPlayerState.LCPPlayerStateStopped -> {
          val previousElement = state.spineElement.previousElement as? LCPSpineElement
            ?: return@runOnUIThread this.log.debug("there's no previous chapter")
          val location = previousElement.position

          val hasChapterChanged = this.hasChapterChangedAfterHandlingLocation(
            location = location,
            playAutomatically = false
          )

          if (!hasChapterChanged) {
            this.updateTrackIndex(
              spineElement = this.spineElementToUpdate!!,
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
    this.playbackObserver?.cancel(true)
    this.spineElementToUpdate = null
  }

  private fun getTrackIndexToPlay(
    chapterTrackIndex: Int,
    offset: Long
  ): Int {
    if (chapterTrackIndex < 0 || chapterTrackIndex > this.tracksToPlay.lastIndex) {
      return -1
    }

    val chapterTrack = this.tracksToPlay[chapterTrackIndex]
    val trackDuration = chapterTrack.link.duration

    if (trackDuration == null) {
      this.log.debug("track {} duration is null", chapterTrack)
      return -1
    }

    val trackDurationMillis = trackDuration.toLong() * 1000L

    // the offset is greater than the track duration, so we need to get the next track
    return if (offset > trackDurationMillis) {
      this.getTrackIndexToPlay(
        chapterTrackIndex = chapterTrackIndex + 1,
        offset = offset - trackDurationMillis
      )
    } else if (offset < 0) {
      if (chapterTrackIndex == 0) {
        -1
      } else {
        val previousTrack = this.tracksToPlay[chapterTrackIndex - 1]
        val previousTrackDuration = previousTrack.link.duration?.toLong() ?: 0L

        // the offset is fewer than 0, so we need to get the previous track
        this.getTrackIndexToPlay(
          chapterTrackIndex = chapterTrackIndex - 1,
          offset = previousTrackDuration + offset
        )
      }
    } else {
      this.trackPlaybackOffset = offset
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
      this.chapterPlaybackOffset = this.spineElementToUpdate?.duration?.millis ?: Long.MAX_VALUE
      this.log.debug("there's no next element")
      return null
    }

    val elementDuration = element.duration?.millis

    if (elementDuration == null) {
      this.log.debug("the spine element {} duration is null", element)
      return null
    }

    return if (offset >= elementDuration) {
      this.getNextElementWithinOffset(
        element = element.nextElement as? LCPSpineElement,
        offset = offset - elementDuration
      )
    } else {
      this.chapterPlaybackOffset = offset
      element
    }
  }

  private fun getPreviousElementWithinOffset(
    element: LCPSpineElement?,
    offset: Long
  ): LCPSpineElement? {
    if (element == null) {
      this.log.debug("there's no previous element")
      this.chapterPlaybackOffset = 0L
      return null
    }

    val elementDuration = element.duration?.millis

    if (elementDuration == null) {
      this.log.debug("the spine element {} duration is null", element)
      return null
    }

    val positiveOffset = abs(offset)

    return if (positiveOffset > elementDuration) {
      this.getPreviousElementWithinOffset(
        element = element.previousElement as? LCPSpineElement,
        offset = elementDuration - positiveOffset
      )
    } else {
      this.chapterPlaybackOffset = elementDuration - positiveOffset
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
      this.getNextElementWithinOffset(
        element = spineElement.nextElement as? LCPSpineElement,
        offset = currentOffset - elementDuration
      )
    } else if (currentOffset < 0) {
      this.getPreviousElementWithinOffset(
        element = spineElement.previousElement as? LCPSpineElement,
        offset = currentOffset
      )
    } else {
      this.chapterPlaybackOffset = currentOffset
      spineElement
    }

    if (chapter == null) {
      this.log.debug("there's no chapter to play")
      return false
    }

    val hasChapterChanged = this.spineElementToUpdate != chapter

    // there's not a current element, so we need to create the playback observer
    if (hasChapterChanged) {
      this.cancelCurrentObserver()
      this.spineElementToUpdate = chapter
      this.startNewSchedulerAfterSomeDelay(
        chapter = chapter,
        playAutomatically = playAutomatically
      )
    }

    return hasChapterChanged
  }

  private fun opClose() {
    this.log.debug("opClose")
    PlayerUIThread.checkIsUIThread()

    this.bookmarkObserver.close()
    this.exoPlayer.stop()
    this.exoPlayer.release()
    this.statusEvents.onCompleted()
    this.playbackObserver?.cancel(true)
  }

  private fun opPause() {
    this.log.debug("opPause")
    PlayerUIThread.checkIsUIThread()

    when (val state = this.stateGet()) {
      LCPPlayerState.LCPPlayerStateInitial -> {
        this.log.debug("not pausing in the initial state")
      }

      is LCPPlayerState.LCPPlayerStatePlaying -> {
        this.log.debug(
          "pausing with trackOffset: {} and chapterOffset: {}", this.trackPlaybackOffset,
          this.chapterPlaybackOffset
        )

        this.exoPlayer.playWhenReady = false

        this.stateSet(LCPPlayerState.LCPPlayerStateStopped(spineElement = state.spineElement))
        this.statusEvents.onNext(
          PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackPaused(
            spineElement = state.spineElement,
            offsetMilliseconds = this.chapterPlaybackOffset
          )
        )
      }

      is LCPPlayerState.LCPPlayerStateStopped -> {
        this.log.debug("not pausing in the stopped state")
      }
    }
  }

  private fun opPlay() {
    this.log.debug("opPlay")
    PlayerUIThread.checkIsUIThread()

    when (val state = this.stateGet()) {
      LCPPlayerState.LCPPlayerStateInitial -> {
        this.movePlayheadToLocation(
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
            offsetMilliseconds = this.chapterPlaybackOffset
          )
        )
      }
    }
  }

  private fun opSetPlaybackRate(newRate: PlayerPlaybackRate) {
    this.log.debug("opSetPlaybackRate: {}", newRate)
    PlayerUIThread.checkIsUIThread()

    this.currentPlaybackRate = newRate
    this.statusEvents.onNext(PlayerEvent.PlayerEventPlaybackRateChanged(newRate))
    this.exoPlayer.playbackParameters = PlaybackParameters(newRate.speed.toFloat())
  }

  private fun preparePlayer(playAutomatically: Boolean, newTrackIndex: Int) {
    val trackToPlay = this.tracksToPlay[newTrackIndex]

    this.log.debug("preparePlayer: {} (offset {})", trackToPlay.link.title, this.trackPlaybackOffset)

    val uri = Uri.parse(trackToPlay.link.hrefURI.toString())

    PlayerUIThread.runOnUIThread {
      this.exoPlayer.setMediaSource(
        ProgressiveMediaSource.Factory(this.dataSourceFactory)
          .createMediaSource(MediaItem.fromUri(uri))
      )
      this.exoPlayer.prepare()
      this.seekToTrackPlaybackOffset()
      this.exoPlayer.playWhenReady = playAutomatically
    }
  }

  private fun schedulePlaybackObserverForSpineElement() {
    this.playbackObserver = this.engineExecutor.scheduleAtFixedRate(
      this.PlaybackObserver(), 1L, 1L, TimeUnit.SECONDS
    )
  }

  private fun seekToTrackPlaybackOffset() {
    this.log.debug("seekTo: {}", this.trackPlaybackOffset)
    PlayerUIThread.runOnUIThread {
      this.exoPlayer.seekTo(this.trackPlaybackOffset)
    }
  }

  private fun startNewSchedulerAfterSomeDelay(
    chapter: LCPSpineElement,
    playAutomatically: Boolean
  ) {
    // we are starting a new scheduler after some small delay so the UI can be updated with the last
    // offset values
    PlayerUIThread.runOnUIThreadDelayed({
      this.updateTrackIndex(
        spineElement = chapter,
        offset = chapter.position.startOffset + this.chapterPlaybackOffset,
        playAutomatically = playAutomatically,
        updateSeek = true
      )

      this.schedulePlaybackObserverForSpineElement()

      if (playAutomatically) {
        this.stateSet(
          LCPPlayerState.LCPPlayerStatePlaying(
            spineElement = chapter
          )
        )
        this.statusEvents.onNext(
          PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackStarted(
            spineElement = chapter,
            offsetMilliseconds = this.chapterPlaybackOffset
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
            offsetMilliseconds = this.chapterPlaybackOffset
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
    val chapterTrackIndex = this.tracksToPlay.indexOfFirst { file ->
      spineElement.itemManifest.originalLink.link.hrefURI == file.link.hrefURI
    }

    if (chapterTrackIndex == -1) {
      this.log.debug("there's no track to play")
      return
    }

    val newIndex = this.getTrackIndexToPlay(
      chapterTrackIndex = chapterTrackIndex,
      offset = offset
    )

    if (newIndex == -1) {
      this.log.debug("there's no track to play")
      return
    }

    if (this.currentTrackIndex != newIndex) {
      this.currentTrackIndex = newIndex
      this.preparePlayer(
        playAutomatically = playAutomatically,
        newTrackIndex = newIndex
      )
    }

    if (updateSeek) {
      this.seekToTrackPlaybackOffset()
    }
  }

  private inner class PlaybackObserver : Runnable {
    override fun run() {
      PlayerUIThread.runOnUIThread {
        when (this@LCPAudioBookPlayer.stateGet()) {
          is LCPPlayerState.LCPPlayerStateInitial,
          is LCPPlayerState.LCPPlayerStateStopped -> {
            // do nothing
          }

          is LCPPlayerState.LCPPlayerStatePlaying -> {
            this@LCPAudioBookPlayer.spineElementToUpdate ?: return@runOnUIThread

            val bookPlayer = this@LCPAudioBookPlayer
            this@LCPAudioBookPlayer.trackPlaybackOffset = bookPlayer.exoPlayer.currentPosition

            bookPlayer.statusEvents.onNext(
              PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackProgressUpdate(
                spineElement = this@LCPAudioBookPlayer.spineElementToUpdate!!,
                offsetMilliseconds = this@LCPAudioBookPlayer.chapterPlaybackOffset
              )
            )

            val location = this@LCPAudioBookPlayer.spineElementToUpdate!!.position.copy(
              currentOffset = this@LCPAudioBookPlayer.chapterPlaybackOffset
            )

            val hasChapterChanged = this@LCPAudioBookPlayer.hasChapterChangedAfterHandlingLocation(
              location = location,
              playAutomatically = true
            )

            if (!hasChapterChanged) {
              this@LCPAudioBookPlayer.updateTrackIndex(
                spineElement = this@LCPAudioBookPlayer.spineElementToUpdate!!,
                offset = location.startOffset + location.currentOffset,
                playAutomatically = true,
                updateSeek = false
              )

              this@LCPAudioBookPlayer.chapterPlaybackOffset += 1000L
            }
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
