package org.librarysimplified.audiobook.open_access

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackProgressUpdate
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOC
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOCItem
import org.slf4j.Logger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An adapter class that delegates operations to a real ExoPlayer, but has extra capabilities
 * such as player offset tracking, event publications, etc.
 */

class ExoAdapter(
  private val logger: Logger,
  private val events: Subject<PlayerEvent>,
  private val exoPlayer: ExoPlayer,
  private val currentReadingOrderItem: () -> PlayerReadingOrderItemType,
  private val toc: PlayerManifestTOC,
  private val tocItemFor: (PlayerReadingOrderItemType, Long) -> PlayerManifestTOCItem
) : Player.Listener, AutoCloseable {

  private val closed =
    AtomicBoolean(false)

  private val stateSubject =
    BehaviorSubject.create<ExoPlayerPlaybackStatusTransition>()
      .toSerialized()

  @Volatile
  private var stateLatest: ExoPlayerPlaybackStatus =
    ExoPlayerPlaybackStatus.INITIAL

  val state: ExoPlayerPlaybackStatus
    get() = this.stateLatest

  val stateObservable: Observable<ExoPlayerPlaybackStatusTransition> =
    this.stateSubject

  fun currentTrackOffsetMilliseconds(): Long {
    if (this.exoPlayer.isCommandAvailable(ExoPlayer.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
      return this.exoPlayer.currentPosition
    }
    return 0L
  }

  override fun onPlayerError(error: PlaybackException) {
    this.logger.error("onPlayerError: ", error)

    this.events.onNext(
      PlayerEvent.PlayerEventError(
        readingOrderItem = this.currentReadingOrderItem.invoke(),
        exception = error,
        errorCode = -1,
        offsetMilliseconds = this.currentTrackOffsetMilliseconds()
      )
    )
  }

  override fun onPlaybackStateChanged(
    playbackState: Int
  ) {
    this.logger.debug(
      "onPlaybackStateChanged: {} ({})", this.nameForState(playbackState),
      playbackState
    )

    when (playbackState) {
      ExoPlayer.STATE_BUFFERING -> {
        this.newState(ExoPlayerPlaybackStatus.BUFFERING)
      }

      ExoPlayer.STATE_ENDED -> {
        this.newState(ExoPlayerPlaybackStatus.CHAPTER_ENDED)
      }

      ExoPlayer.STATE_IDLE -> {
        this.newState(ExoPlayerPlaybackStatus.INITIAL)
      }

      ExoPlayer.STATE_READY -> {
        if (this.exoPlayer.isPlaying) {
          this.newState(ExoPlayerPlaybackStatus.PLAYING)
        } else {
          this.newState(ExoPlayerPlaybackStatus.PAUSED)
        }
      }

      else -> {
        this.logger.error("Unrecognized player state: {}", playbackState)
      }
    }
  }

  private fun newState(new: ExoPlayerPlaybackStatus) {
    val old = this.state
    val transition = ExoPlayerPlaybackStatusTransition(old, new)
    this.stateLatest = new
    this.stateSubject.onNext(transition)
  }

  override fun onPlayWhenReadyChanged(
    playWhenReady: Boolean,
    reason: Int
  ) {
    this.logger.debug("onPlayWhenReadyChanged: {} {})", playWhenReady, reason)

    if (this.exoPlayer.isPlaying) {
      this.newState(ExoPlayerPlaybackStatus.PLAYING)
    } else {
      if (this.exoPlayer.isLoading) {
        this.newState(ExoPlayerPlaybackStatus.BUFFERING)
      } else {
        this.newState(ExoPlayerPlaybackStatus.PAUSED)
      }
    }
  }

  private fun nameForState(playbackState: Int): String {
    return when (playbackState) {
      ExoPlayer.STATE_BUFFERING -> {
        "Buffering"
      }

      ExoPlayer.STATE_ENDED -> {
        "Ended"
      }

      ExoPlayer.STATE_IDLE -> {
        "Idle"
      }

      ExoPlayer.STATE_READY -> {
        "Ready"
      }

      else -> {
        "Unrecognized state"
      }
    }
  }

  fun broadcastPlaybackPosition() {
    val readingOrderItem =
      this.currentReadingOrderItem.invoke()
    val offsetMilliseconds =
      this.currentTrackOffsetMilliseconds()
    val tocItem =
      this.tocItemFor.invoke(readingOrderItem, offsetMilliseconds)
    val durationRemaining =
      this.toc.totalDurationRemaining(tocItem, offsetMilliseconds)

    this.events.onNext(
      PlayerEventPlaybackProgressUpdate(
        readingOrderItem = readingOrderItem,
        offsetMilliseconds = offsetMilliseconds,
        tocItem = tocItem,
        totalRemainingBookTime = durationRemaining
      )
    )
  }

  override fun close() {
    if (this.closed.compareAndSet(false, true)) {
      this.stateSubject.onComplete()
    }
  }

  fun playIfNotAlreadyPlaying() {
    if (this.exoPlayer.isPlaying) {
      return
    }
    this.exoPlayer.play()
  }
}