package org.librarysimplified.audiobook.mocking

import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent
import org.librarysimplified.audiobook.api.PlayerSleepTimerType
import rx.Observable
import rx.subjects.BehaviorSubject

/**
 * A sleep timer that does nothing at all.
 */

class MockingSleepTimer : PlayerSleepTimerType {

  private val events = BehaviorSubject.create<PlayerSleepTimerEvent>()
  private var running: PlayerSleepTimerType.Running? = null
  private var closed: Boolean = false
  private var paused: Boolean = false
  private var time: Duration? = null

  override fun setDuration(time: Duration?) {
    this.time = time
  }

  override fun start() {
    this.running = PlayerSleepTimerType.Running(this.paused, time)
    this.events.onNext(PlayerSleepTimerEvent.PlayerSleepTimerRunning(this.paused, time))
  }

  override fun cancel() {
    this.events.onNext(PlayerSleepTimerEvent.PlayerSleepTimerCancelled(this.running?.duration))
  }

  override fun finish() {
    this.events.onNext(PlayerSleepTimerEvent.PlayerSleepTimerFinished)
  }

  override fun pause() {
    this.paused = true
  }

  override fun unpause() {
    this.paused = false
  }

  override val status: Observable<PlayerSleepTimerEvent>
    get() = this.events

  override fun close() {
    this.closed = true
    this.events.onCompleted()
  }

  override val isClosed: Boolean
    get() = this.closed

  override val isRunning: PlayerSleepTimerType.Running?
    get() = this.running

  override val hasCurrentTime: Boolean
    get() = this.time != null
}
