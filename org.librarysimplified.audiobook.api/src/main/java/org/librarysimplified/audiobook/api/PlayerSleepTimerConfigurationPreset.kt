package org.librarysimplified.audiobook.api

import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration.WithDuration

/**
 * The default selectable sleep timer configurations.
 */

enum class PlayerSleepTimerConfigurationPreset(
  val configuration: PlayerSleepTimerConfiguration
) {
  /**
   * The sleep timer will finish now. This option is primarily useful for debugging.
   */

  NOW(WithDuration(Duration.standardSeconds(1L))),

  /**
   * The sleep timer will never finish. This is essentially used to switch off the sleep timer.
   */

  OFF(PlayerSleepTimerConfiguration.Off),

  /**
   * The sleep timer will finish in 15 minutes.
   */

  MINUTES_15(WithDuration(Duration.standardMinutes(15L))),

  /**
   * The sleep timer will finish in 30 minutes.
   */

  MINUTES_30(WithDuration(Duration.standardMinutes(30L))),

  /**
   * The sleep timer will finish in 45 minutes.
   */

  MINUTES_45(WithDuration(Duration.standardMinutes(45L))),

  /**
   * The sleep timer will finish in 60 minutes.
   */

  MINUTES_60(WithDuration(Duration.standardMinutes(60L))),

  /**
   * The sleep timer will finish at the end of the current chapter.
   */

  END_OF_CHAPTER(PlayerSleepTimerConfiguration.EndOfChapter)
}
