package org.librarysimplified.audiobook.api

/**
 * The type of sleep timer events.
 */

sealed class PlayerSleepTimerEvent {

  /**
   * The sleep timer is currently running. This state will be published frequently while the sleep
   * timer is counting down. If a duration was specified when the timer was started, the given
   * duration indicates the amount of time remaining.
   */

  data class PlayerSleepTimerStatusChanged(
    val oldStatus: PlayerSleepTimerType.Status,
    val newStatus: PlayerSleepTimerType.Status
  ) : PlayerSleepTimerEvent()

  /**
   * The sleep timer ran to completion.
   */

  data object PlayerSleepTimerFinished : PlayerSleepTimerEvent()
}
