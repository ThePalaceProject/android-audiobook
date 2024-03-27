package org.librarysimplified.audiobook.api

/**
 * The playback status of the player.
 */

enum class PlayerPlaybackStatus {

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

  PAUSED
}
