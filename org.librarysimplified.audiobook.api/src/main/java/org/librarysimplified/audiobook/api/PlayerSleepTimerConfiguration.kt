package org.librarysimplified.audiobook.api

import org.joda.time.Duration

/**
 * Configuration values for the sleep timer.
 */

enum class PlayerSleepTimerConfiguration(val duration: Duration?) {

  /**
   * The sleep timer will finish now. This option is primarily useful for debugging.
   */

  NOW(Duration.standardSeconds(1L)),

  /**
   * The sleep timer will never finish. This is essentially used to switch off the sleep timer.
   */

  OFF(null),

  /**
   * The sleep timer will finish in 15 minutes.
   */

  MINUTES_15(Duration.standardMinutes(15L)),

  /**
   * The sleep timer will finish in 30 minutes.
   */

  MINUTES_30(Duration.standardMinutes(30L)),

  /**
   * The sleep timer will finish in 45 minutes.
   */

  MINUTES_45(Duration.standardMinutes(45L)),

  /**
   * The sleep timer will finish in 60 minutes.
   */

  MINUTES_60(Duration.standardMinutes(60L)),

  /**
   * The sleep timer will finish at the end of the current chapter.
   */

  END_OF_CHAPTER(null)
}
