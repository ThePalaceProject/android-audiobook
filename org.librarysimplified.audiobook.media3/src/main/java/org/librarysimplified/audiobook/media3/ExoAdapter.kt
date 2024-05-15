package org.librarysimplified.audiobook.media3

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventChapterCompleted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackProgressUpdate
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOC
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOCItem
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsReadingOrderItem
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
  private val currentReadingOrderItem: () -> ExoReadingOrderItemHandle,
  private val toc: PlayerManifestTOC,
  private val isStreamingNow: () -> Boolean,
) : Player.Listener, AutoCloseable {

  private val closed =
    AtomicBoolean(false)

  private val stateSubject =
    BehaviorSubject.create<ExoPlayerPlaybackStatusTransition>()
      .toSerialized()

  /**
   * The ExoPlayer implementation exposes an [ExoPlayer.STATE_BUFFERING] state that's
   * reached when the player starts buffering. Unfortunately, it's perfectly normal for the
   * player to also publish other states before the player has actually finished buffering.
   * Therefore, thanks to this bad design, it's necessary for us to track buffering separately;
   * set the flag when buffering starts, and unset it when something significant happens
   * (like [ExoPlayer.STATE_READY]).
   */

  @Volatile
  internal var isBufferingNow: Boolean =
    false

  /**
   * When we want to skip playback to a particular point in a particular chapter, we tell
   * the player to prepare that chapter's audio, and then immediately seek to the given offset
   * in milliseconds. Unfortunately, because we observe the underlying player's position and
   * place that position into "buffering" events that we then publish, any UI observing these
   * events will see the playback position first seek to position 0 in the chapter, followed
   * by the actual playback position when buffering finishes. To prevent this, we publish
   * a fake "buffering" position each time we tell the player to prepare, and publish this
   * fake position in the events.
   */

  @Volatile
  private var fakeBufferingPosition: PlayerMillisecondsReadingOrderItem? = null

  @Volatile
  private var stateLatest: ExoPlayerPlaybackStatus =
    ExoPlayerPlaybackStatus.INITIAL

  /**
   * We track the URI of the data source that was most recently given to [ExoPlayer.setMediaSource].
   * The reason for this is that preparation is expensive, and we don't want to redundantly
   * prepare a new data source if one for the same URI is already prepared.
   */

  @Volatile
  private var preparedURI: Uri? = null

  /**
   * We track the current TOC item when the player is playing. This allows us to publish
   * "chapter completed" events when we cross a TOC item boundary within a reading order item.
   */

  @Volatile
  private var tocItemTracked: PlayerManifestTOCItem? = null

  val state: ExoPlayerPlaybackStatus
    get() = this.stateLatest

  val stateObservable: Observable<ExoPlayerPlaybackStatusTransition> =
    this.stateSubject

  fun currentTrackOffsetMilliseconds(): PlayerMillisecondsReadingOrderItem {
    if (this.isBufferingNow) {
      val fakeOffset = this.fakeBufferingPosition
      if (fakeOffset != null) {
        this.fakeBufferingPosition = null
        return fakeOffset
      }
    }

    if (this.exoPlayer.isCommandAvailable(ExoPlayer.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
      return PlayerMillisecondsReadingOrderItem(this.exoPlayer.currentPosition)
    }
    return PlayerMillisecondsReadingOrderItem(0L)
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
        this.isBufferingNow = true
        this.newState(ExoPlayerPlaybackStatus.BUFFERING)
      }

      ExoPlayer.STATE_ENDED -> {
        this.isBufferingNow = false
        this.newState(ExoPlayerPlaybackStatus.CHAPTER_ENDED)
      }

      ExoPlayer.STATE_IDLE -> {
        this.newState(ExoPlayerPlaybackStatus.INITIAL)
      }

      ExoPlayer.STATE_READY -> {
        this.isBufferingNow = false
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
        this.isBufferingNow = true
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
      this.toc.lookupTOCItem(readingOrderItem.id, offsetMilliseconds)
    val positionMetadata =
      this.toc.positionMetadataFor(
        readingOrderItemID = readingOrderItem.id,
        readingOrderItemOffset = offsetMilliseconds,
        readingOrderItemInterval = readingOrderItem.interval
      )

    this.events.onNext(
      PlayerEventPlaybackProgressUpdate(
        isStreaming = this.isStreamingNow(),
        offsetMilliseconds = offsetMilliseconds,
        positionMetadata = positionMetadata,
        readingOrderItem = readingOrderItem,
      )
    )

    /*
     * Compare TOC items when the player is actually playing. If the player isn't playing,
     * then don't track TOC items.
     */

    if (this.exoPlayer.isPlaying) {
      val tocItemPrevious = this.tocItemTracked
      if (tocItemPrevious != null) {
        if (tocItemPrevious.index != tocItem.index) {
          this.events.onNext(
            PlayerEventChapterCompleted(
              readingOrderItem = readingOrderItem,
              positionMetadata = positionMetadata,
              isStreaming = this.isStreamingNow(),
            )
          )
        }
      }
      this.tocItemTracked = tocItem
    } else {
      this.tocItemTracked = null
    }
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

  fun prepare(
    dataSourceFactory: DataSource.Factory,
    targetURI: Uri,
    offset: PlayerMillisecondsReadingOrderItem
  ) {
    this.logger.debug(
      "prepare: [{}] Setting media source and preparing player now.",
      targetURI
    )

    if (this.preparedURI == targetURI) {
      this.logger.debug(
        "prepare: [{}] A media source with this URI is already prepared; skipping preparation.",
        targetURI
      )
      return
    }

    this.preparedURI = targetURI
    this.fakeBufferingPosition = offset

    val mediaSource =
      ProgressiveMediaSource.Factory(dataSourceFactory)
        .createMediaSource(MediaItem.fromUri(targetURI))
    this.exoPlayer.setMediaSource(mediaSource)
    this.exoPlayer.prepare()

    this.logger.debug(
      "prepare: [{}] Scheduled prepare on ExoPlayer.",
      targetURI
    )
  }
}
