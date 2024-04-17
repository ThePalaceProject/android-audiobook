package org.librarysimplified.audiobook.audioengine

import io.audioengine.mobile.AudioEngine
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposables
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerPlaybackIntention
import org.librarysimplified.audiobook.api.PlayerPlaybackRate
import org.librarysimplified.audiobook.api.PlayerPlaybackStatus
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.librarysimplified.audiobook.api.PlayerUIThread.runOnUIThread
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The primary Findaway based player.
 */

class FindawayPlayer(
  private val book: FindawayAudioBook,
  private val engine: AudioEngine
) : PlayerType {

  private val log =
    LoggerFactory.getLogger(FindawayPlayer::class.java)

  companion object {
    const val ENGINE_REPORTED_UNKNOWN_CHAPTER: Int = 700_000_000
    const val NO_SENSIBLE_ERROR_CODE = 0x4f484e4f
  }

  private val statusExecutor: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor { r ->
      val thread = Thread(r)
      thread.name = "FindawayPlayer.status[${thread.id}]"
      thread.priority = Thread.MIN_PRIORITY
      thread
    }

  private val bookmarkObserver: FindawayBookmarkObserver

  private val closed: AtomicBoolean =
    AtomicBoolean(false)

  private val eventSource: Subject<PlayerEvent> =
    BehaviorSubject.create<PlayerEvent>().toSerialized()

  private val resources = CompositeDisposable()
  private val id: String

  /**
   * The state the user has asked the player to be in.
   */

  @Volatile
  private var intention: PlayerPlaybackIntention =
    PlayerPlaybackIntention.SHOULD_BE_STOPPED

  private val engineAdapter: FindawayAdapter

  /**
   * The "constructor" for the class.
   */

  init {
    this.resources.add(Disposables.fromAction(this.statusExecutor::shutdown))

    this.id =
      Integer.toString(this.hashCode(), 16)

    this.engineAdapter =
      FindawayAdapter(
        logger = this.log,
        events = this.eventSource,
        engine = this.engine,
        id = this.id,
        book = this.book,
        intention = {
          this.intention
        }
      )

    /*
     * Register a bookmark observer.
     */

    this.bookmarkObserver =
      FindawayBookmarkObserver.create(
        player = this,
        onBookmarkCreate = this.eventSource::onNext,
        isStreamingNow = {
          this.engineAdapter.isStreamingNow
        }
      )
    this.resources.add(
      Disposables.fromAction(this.bookmarkObserver::close)
    )

    /*
     * Register an observer that will periodically ask the adapter for it's current playback
     * position.
     */

    this.statusExecutor.scheduleAtFixedRate({
      runOnUIThread {
        if (this.intention == PlayerPlaybackIntention.SHOULD_BE_PLAYING) {
          this.engineAdapter.broadcastPlaybackPosition()
        }
      }
    }, 500L, 500L, TimeUnit.MILLISECONDS)

    this.engine.playbackEngine.manageAudioFocus(false)
    this.engine.playbackEngine.manageBecomingNoisy(false)
    this.log.debug("[{}]: Player created", this.id)
  }

  private fun opEnginePlayFromCurrentPosition() {
    this.log.debug("[{}]: opEnginePlayFromCurrentPosition", this.id)
    PlayerUIThread.checkIsUIThread()

    this.engineAdapter.playFromCurrentPosition()
  }

  private fun opEnginePause() {
    this.log.debug("[{}]: opEnginePause", this.id)
    PlayerUIThread.checkIsUIThread()

    this.engine.playbackEngine.pause()
  }

  private fun opEngineStop() {
    this.log.debug("[{}]: opEngineStop", this.id)
    PlayerUIThread.checkIsUIThread()

    this.engine.playbackEngine.stop()
  }

  private fun checkNotClosed() {
    if (this.closed.get()) {
      throw IllegalStateException("Player has been closed")
    }
  }

  private fun opSkipForward(
    milliseconds: Long
  ) {
    this.log.debug("[{}]: opSkipForward: {}", this.id, milliseconds)
    PlayerUIThread.checkIsUIThread()

    this.engineAdapter.skipForward(milliseconds)
  }

  private fun opSkipBack(milliseconds: Long) {
    this.log.debug("[{}]: opSkipBack: {}", this.id, milliseconds)
    PlayerUIThread.checkIsUIThread()

    this.engineAdapter.skipBack(milliseconds)
  }

  private fun opSkipToPreviousChapter(
    offset: Long
  ) {
    this.log.debug("[{}]: opSkipToPreviousChapter: {}", this.id, offset)
    PlayerUIThread.checkIsUIThread()

    this.engineAdapter.skipToPreviousChapter(offset)
  }

  private fun opSkipToNextChapter(
    offset: Long
  ) {
    this.log.debug("[{}]: opSkipToNextChapter: {}", this.id, offset)
    PlayerUIThread.checkIsUIThread()

    this.engineAdapter.skipToNextChapter(offset)
  }

  private fun opBookmarkDelete(bookmark: PlayerBookmark) {
    this.log.debug("[{}]: opBookmarkDelete: {}", this.id, bookmark)
    PlayerUIThread.checkIsUIThread()

    this.eventSource.onNext(PlayerEvent.PlayerEventDeleteBookmark(bookmark))
  }

  private fun opBookmark() {
    this.log.debug("[{}]: opBookmark", this.id)
    PlayerUIThread.checkIsUIThread()

    this.engineAdapter.bookmark()
  }

  override fun play() {
    this.checkNotClosed()
    this.log.debug("[{}]: play", this.id)

    this.intention =
      PlayerPlaybackIntention.SHOULD_BE_PLAYING

    runOnUIThread {
      this.opEnginePlayFromCurrentPosition()
    }
  }

  override fun pause() {
    this.checkNotClosed()
    this.log.debug("[{}]: pause", this.id)

    this.intention =
      PlayerPlaybackIntention.SHOULD_BE_STOPPED

    runOnUIThread {
      this.opEnginePause()
    }
  }

  override fun skipPlayhead(
    milliseconds: Long
  ) {
    this.checkNotClosed()
    this.log.debug("[{}]: skipPlayhead: {}", this.id, milliseconds)

    runOnUIThread {
      when {
        milliseconds == 0L -> {
          // Nothing to do!
        }
        milliseconds > 0L -> {
          this.opSkipForward(milliseconds)
        }
        else -> {
          this.opSkipBack(milliseconds)
        }
      }
    }
  }

  override fun skipToNextChapter(
    offset: Long
  ) {
    this.checkNotClosed()

    runOnUIThread {
      this.opSkipToNextChapter(offset)
    }
  }

  override fun skipToPreviousChapter(
    offset: Long
  ) {
    this.checkNotClosed()

    runOnUIThread {
      this.opSkipToPreviousChapter(offset)
    }
  }

  override val events: Observable<PlayerEvent>
    get() = this.eventSource

  override val playbackStatus: PlayerPlaybackStatus
    get() = this.engineAdapter.playbackStatus

  override val playbackIntention: PlayerPlaybackIntention
    get() = this.intention

  override fun movePlayheadToLocation(
    location: PlayerPosition
  ) {
    this.checkNotClosed()

    runOnUIThread {
      this.engineAdapter.movePlayheadToLocation(location)
    }
  }

  override fun movePlayheadToBookStart() {
    this.checkNotClosed()

    runOnUIThread {
      this.engineAdapter.movePlayheadToLocation(this.book.readingOrder.first().startingPosition)
    }
  }

  override fun seekTo(milliseconds: Long) {
    this.checkNotClosed()

    runOnUIThread {
      this.engineAdapter.seekTo(milliseconds)
    }
  }

  override fun bookmark() {
    this.checkNotClosed()

    runOnUIThread {
      this.opBookmark()
    }
  }

  override fun bookmarkDelete(
    bookmark: PlayerBookmark
  ) {
    this.checkNotClosed()

    runOnUIThread {
      this.opBookmarkDelete(bookmark)
    }
  }

  override val isClosed: Boolean
    get() = this.closed.get()

  override fun close() {
    if (this.closed.compareAndSet(false, true)) {
      this.log.debug("[{}]: close", this.id)
      this.opEngineStop()
      this.eventSource.onComplete()
      this.bookmarkObserver.close()
      this.resources.dispose()
    }
  }

  override var playbackRate: PlayerPlaybackRate
    get() =
      this.engineAdapter.currentPlaybackRate
    set(value) {
      this.engineAdapter.setPlaybackRate(value)
    }
}
