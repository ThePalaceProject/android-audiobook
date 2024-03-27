package org.librarysimplified.audiobook.api

import io.reactivex.Observable

/**
 * A player for a book.
 */

interface PlayerType : AutoCloseable {

  /**
   * Close this audio book player. All subsequent method calls on this player will throw
   * {@link java.lang.IllegalStateException} indicating that the player is closed.
   */

  @Throws(java.lang.IllegalStateException::class)
  override fun close()

  /**
   * The current player playback status
   */

  val playbackStatus: PlayerPlaybackStatus

  /**
   * The current playback intention.
   */

  val playbackIntention: PlayerPlaybackIntention

  /**
   * The playback rate for the player.
   *
   * @throws java.lang.IllegalStateException If and only if the player is closed
   */

  var playbackRate: PlayerPlaybackRate

  /**
   * @return `true` if and only if {@link #close()} has been called on this player
   */

  val isClosed: Boolean

  /**
   * An observable that publishes player status updates.
   *
   * @throws java.lang.IllegalStateException If and only if the player is closed
   */

  val events: Observable<PlayerEvent>

  /**
   * Play at current playhead location.
   *
   * Sets the playback intention to [PlayerPlaybackIntention.SHOULD_BE_PLAYING].
   *
   * @throws java.lang.IllegalStateException If and only if the player is closed
   */

  fun play()

  /**
   * Pause playback
   *
   * Sets the playback intention to [PlayerPlaybackIntention.SHOULD_BE_STOPPED].
   *
   * @throws java.lang.IllegalStateException If and only if the player is closed
   */

  fun pause()

  /**
   * Skip to the next chapter at the given offset.
   *
   * @throws java.lang.IllegalStateException If and only if the player is closed
   */

  fun skipToNextChapter(offset: Long)

  /**
   * Skip to the previous chapter at the given offset.
   *
   * @throws java.lang.IllegalStateException If and only if the player is closed
   */

  fun skipToPreviousChapter(offset: Long)

  /**
   * Skip forwards/backwards, possibly across chapter boundaries. If the given parameter is
   * positive, skip forwards. If the given parameter is negative, skip backwards. If the given
   * parameter is `0`, do nothing.
   *
   * Note: Implementations are not required to support skipping over multiple chapters in a
   * single skip. Please use the explicit `movePlayheadToLocation` API if you want to perform large
   * jumps.
   *
   * @throws java.lang.IllegalStateException If and only if the player is closed
   */

  fun skipPlayhead(milliseconds: Long)

  /**
   * Skip forward 30 seconds and start playback
   *
   * @throws java.lang.IllegalStateException If and only if the player is closed
   */

  fun skipForward() {
    this.skipPlayhead(30_000L)
  }

  /**
   * Skip back 30 seconds and start playback
   *
   * @throws java.lang.IllegalStateException If and only if the player is closed
   */

  fun skipBack() {
    this.skipPlayhead(-30_000L)
  }

  /**
   * Move playhead but do not change whether the player is playing or not. This is useful for state
   * restoration where we want to prepare for playback at a specific point, but playback has not yet
   * been requested.
   *
   * @throws java.lang.IllegalStateException If and only if the player is closed
   */

  fun movePlayheadToLocation(location: PlayerPosition)

  /**
   * Move playhead to the start of the book but do not start playback.
   *
   * @throws java.lang.IllegalStateException If and only if the player is closed
   */

  fun movePlayheadToBookStart()

  /**
   * Get the player's current position as player bookmark.
   */

  fun getCurrentPositionAsPlayerBookmark(): PlayerBookmark?
}
