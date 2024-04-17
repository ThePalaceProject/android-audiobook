package org.librarysimplified.audiobook.api

/**
 * The intention of the user with regard to playback.
 */

enum class PlayerPlaybackIntention {

  /*
   * The user wants the player to be playing.
   */

  SHOULD_BE_PLAYING,

  /*
   * The user wants the player to be stopped.
   */

  SHOULD_BE_STOPPED
}
