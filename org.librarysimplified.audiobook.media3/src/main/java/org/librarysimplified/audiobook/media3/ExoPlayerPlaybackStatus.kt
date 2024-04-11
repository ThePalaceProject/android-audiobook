package org.librarysimplified.audiobook.media3

/**
 * The playback status of the player.
 */

enum class ExoPlayerPlaybackStatus {

  /**
   * The player is in the initialized state.
   */

  INITIAL,

  /**
   * The player is currently buffering audio and can't play.
   */

  BUFFERING,

  /**
   * The player is currently playing audio.
   */

  PLAYING,

  /**
   * The player is currently paused.
   */

  PAUSED,

  /**
   * The chapter the player was playing has ended.
   */

  CHAPTER_ENDED
}

/**
 * The player transitioned from one playback state to another.
 */

data class ExoPlayerPlaybackStatusTransition(
  val oldState: ExoPlayerPlaybackStatus,
  val newState: ExoPlayerPlaybackStatus
)
