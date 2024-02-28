package org.librarysimplified.audiobook.api

import io.reactivex.Observable
import net.jcip.annotations.ThreadSafe

/**
 * The interface exposed by sleep timer implementations.
 *
 * Implementations of this interface are required to be thread-safe. That is, methods and properties
 * may be safely called/accessed from any thread.
 */

@ThreadSafe
interface PlayerSleepTimerType {

  /**
   * Start the timer. If a duration has been given, the timer will count down over the given duration
   * and will periodically publish events giving the remaining time. If no duration is given, the
   * timer will wait indefinitely for a call to {@link #finish()}. If the timer is paused, the
   * timer will be unpaused.
   */

  fun start()

  /**
   * Sets the configuration of the timer.
   *
   * @param configuration The configuration
   */

  fun configure(configuration: PlayerSleepTimerConfiguration)

  /**
   * Cancel the timer. The timer will stop and will publish an event indicating the current
   * state.
   */

  fun cancel()

  /**
   * Pause the timer. The timer will pause and will publish an event indicating the current
   * state.
   */

  fun pause()

  /**
   * Unpause the timer. The timer will unpause and will publish an event indicating the current
   * state.
   */

  fun unpause()

  /**
   * Finish the timer. This makes the timer behave exactly as if a duration had been given to
   * start and the duration has elapsed. If the timer is paused, the timer will be unpaused.
   */

  fun finish()

  /**
   * An observable indicating the current state of the timer. The observable is buffered such
   * that each new subscription will receive the most recently published status event, and will
   * then receive new status events as they are published.
   *
   * Events are guaranteed to be published on the Android UI thread.
   */

  val events: Observable<PlayerSleepTimerEvent>

  /**
   * The current configuration.
   */

  val configuration: PlayerSleepTimerConfiguration

  /*
   * The current timer status.
   */

  sealed class Status {
    abstract val configuration: PlayerSleepTimerConfiguration

    data class Running(
      override val configuration: PlayerSleepTimerConfiguration
    ) : Status()

    data class Paused(
      override val configuration: PlayerSleepTimerConfiguration
    ) : Status()

    data class Stopped(
      override val configuration: PlayerSleepTimerConfiguration
    ) : Status()
  }

  val status: Status
}
