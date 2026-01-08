package org.librarysimplified.audiobook.api

/**
 * The reason that audio is paused.
 */

enum class PlayerPauseReason {

  /**
   * The player is paused because it started out paused.
   */

  PAUSE_REASON_INITIALLY_PAUSED,

  /**
   * The player is paused because the user pressed the pause button.
   */

  PAUSE_REASON_USER_EXPLICITLY_PAUSED,

  /**
   * The player is paused because the bluetooth audio device changed.
   */

  PAUSE_REASON_BLUETOOTH_DEVICE_CHANGED,

  /**
   * The player is paused because audio focus was lost (perhaps due to a phone call).
   */

  PAUSE_REASON_AUDIO_FOCUS_LOST,

  /**
   * The player is paused because the sleep timer completed.
   */

  PAUSE_REASON_SLEEP_TIMER
}
