package org.librarysimplified.audiobook.api

import org.joda.time.Duration

/**
 * The possible sleep timer configurations.
 */

sealed class PlayerSleepTimerConfiguration {

  /**
   * The sleep timer will be completed when the given duration has elapsed.
   */

  data class WithDuration(
    val duration: Duration
  ) : PlayerSleepTimerConfiguration()

  /**
   * The sleep timer will be completed at the end of the chapter.
   */

  data object EndOfChapter : PlayerSleepTimerConfiguration()

  /**
   * The sleep timer is off.
   */

  data object Off : PlayerSleepTimerConfiguration()
}
