package org.librarysimplified.audiobook.media3

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventChapterCompleted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackProgressUpdate
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOC
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOCItem
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsReadingOrderItem
import org.slf4j.Logger
import org.slf4j.MDC
import java.net.URI
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
  private val manifest: PlayerManifest,
  private val toc: PlayerManifestTOC,
  private val isStreamingNow: () -> Boolean,
  private val authorizationHandler: PlayerAuthorizationHandlerType,
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

  /**
   * The underlying ExoPlayer implementation is extremely vulnerable to corrupted audio data.
   * It might publish a [PlaybackException] once, or it might not. Once it has published one,
   * it may not ever publish another, but it'll fail to play audio until a new file is prepared.
   * Therefore, we save the exception so that we know the player was broken at some point.
   */

  private var savedError: PlaybackException? = null

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
    this.setupMDC()
    this.logger.error("onPlayerError: ", error)

    this.savedError = error

    /*
     * Detect a 401 error. This is treated differently from an ordinary playback error because
     * we need views to be able to show login buttons when credentials have expired. We avoid
     * publishing a [PlayerEventError] as this would (at the time of writing) cause the 401 error
     * message to be overwritten in the player view.
     */

    val cause = error.cause
    if (cause is HttpDataSource.InvalidResponseCodeException) {
      if (cause.responseCode == 401) {
        this.authorizationHandler.onAuthorizationIsInvalid(
          source = PlayerManifestLink.LinkBasic(URI.create("urn:unavailable")),
          kind = PlayerDownloadRequest.Kind.CHAPTER
        )
        return
      }
    }

    this.events.onNext(
      PlayerEvent.PlayerEventError(
        palaceId = this.manifest.palaceId,
        readingOrderItem = this.currentReadingOrderItem.invoke(),
        exception = error,
        errorCode = error.errorCode,
        errorCodeName = error.errorCodeName,
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
        this.isBufferingNow = false
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

    this.logger.debug("newState: {} -> {}", old, new)
    this.stateLatest = new
    this.stateSubject.onNext(transition)
  }

  override fun onPlayWhenReadyChanged(
    playWhenReady: Boolean,
    reason: Int
  ) {
    this.logger.debug("onPlayWhenReadyChanged: {} {})", playWhenReady, reason)

    when (playWhenReady) {
      true -> {
        if (this.exoPlayer.isPlaying) {
          this.newState(ExoPlayerPlaybackStatus.PLAYING)
          return
        }
        if (this.exoPlayer.isLoading) {
          this.isBufferingNow = true
          this.newState(ExoPlayerPlaybackStatus.BUFFERING)
        }
      }
      false -> {
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
        palaceId = this.manifest.palaceId,
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
              palaceId = this.manifest.palaceId,
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
    this.setupMDC()
    MDC.put("PrepareURI", targetURI.toString())

    this.logger.debug(
      "prepare: [{}] Setting media source and preparing player now.",
      targetURI
    )

    if (this.preparedURI == targetURI) {
      if (!this.isPlayerBroken()) {
        this.logger.debug(
          "prepare: [{}] A media source with this URI is already prepared; skipping preparation.",
          targetURI
        )
        return
      }
    }

    this.preparedURI = targetURI
    this.fakeBufferingPosition = offset
    this.savedError = null

    val newSource =
      ProgressiveMediaSource.Factory(dataSourceFactory)
        .createMediaSource(MediaItem.fromUri(targetURI))

    this.exoPlayer.setMediaSource(newSource)
    this.exoPlayer.prepare()

    this.logger.debug(
      "prepare: [{}] Scheduled prepare on ExoPlayer.",
      targetURI
    )
  }

  private fun isPlayerBroken(): Boolean {
    return this.savedError != null
  }

  private fun setupMDC() {
    MDC.put("BookTitle", this.manifest.metadata.title)
    MDC.put("BookID", this.manifest.metadata.identifier)
    this.manifest.metadata.encrypted?.let {
      MDC.put("BookDRMScheme", it.scheme)
    }
  }
}
