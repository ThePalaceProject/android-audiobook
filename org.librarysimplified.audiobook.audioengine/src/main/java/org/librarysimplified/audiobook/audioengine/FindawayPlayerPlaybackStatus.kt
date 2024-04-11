package org.librarysimplified.audiobook.audioengine

/**
 * The playback status of the player.
 */

enum class FindawayPlayerPlaybackStatus {

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

data class FindawayPlayerPlaybackStatusTransition(
  val oldState: FindawayPlayerPlaybackStatus,
  val newState: FindawayPlayerPlaybackStatus
)
