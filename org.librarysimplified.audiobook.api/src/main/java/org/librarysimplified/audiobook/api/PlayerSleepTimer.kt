package org.librarysimplified.audiobook.api

import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.BehaviorSubject
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerSleepTimer.TimerCommand.Cancel
import org.librarysimplified.audiobook.api.PlayerSleepTimer.TimerCommand.Finish
import org.librarysimplified.audiobook.api.PlayerSleepTimer.TimerCommand.Pause
import org.librarysimplified.audiobook.api.PlayerSleepTimer.TimerCommand.Reconfigure
import org.librarysimplified.audiobook.api.PlayerSleepTimer.TimerCommand.Start
import org.librarysimplified.audiobook.api.PlayerSleepTimer.TimerCommand.Unpause
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration.EndOfChapter
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration.Off
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration.WithDuration
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent.PlayerSleepTimerFinished
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent.PlayerSleepTimerStatusChanged
import org.librarysimplified.audiobook.api.PlayerSleepTimerType.Status
import org.librarysimplified.audiobook.api.PlayerSleepTimerType.Status.Paused
import org.librarysimplified.audiobook.api.PlayerSleepTimerType.Status.Running
import org.librarysimplified.audiobook.api.PlayerSleepTimerType.Status.Stopped
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

object PlayerSleepTimer : PlayerSleepTimerType {

  private val oneSecond =
    Duration.standardSeconds(1L)

  private val timerExecutor =
    Executors.newSingleThreadExecutor { runnable ->
      val thread = Thread(runnable)
      thread.priority = Thread.MIN_PRIORITY
      thread.name = "org.librarysimplified.audiobook.api.SleepTimer"
      return@newSingleThreadExecutor thread
    }

  @Volatile
  private var statusNow: Status =
    Stopped(configuration = Off)

  private val eventsSubject =
    BehaviorSubject.create<PlayerSleepTimerEvent>()
      .toSerialized()

  private val eventsOnUI: Observable<PlayerSleepTimerEvent> =
    this.eventsSubject.observeOn(AndroidSchedulers.mainThread())

  private val commandQueue =
    LinkedBlockingQueue<TimerCommand>()

  /**
   * The type of timer commands. Commands are submitted onto a command queue to be executed
   * on the timer thread.
   */

  private sealed class TimerCommand {

    data object Start : TimerCommand()

    data object Cancel : TimerCommand()

    data object Pause : TimerCommand()

    data object Unpause : TimerCommand()

    data object Finish : TimerCommand()

    data class Reconfigure(val configuration: PlayerSleepTimerConfiguration) : TimerCommand()
  }

  init {
    this.timerExecutor.execute {
      this.execute()
    }
  }

  private fun execute() {
    while (true) {
      try {
        when (val command = this.commandQueue.poll(1L, TimeUnit.SECONDS)) {
          null -> {
            // No commands received.
          }

          Start -> this.opStart()
          Cancel -> this.opCancel()
          is Reconfigure -> this.opReconfigure(command.configuration)
          Pause -> this.opPause()
          Unpause -> this.opUnpause()
          Finish -> this.onFinish()
        }

        this.opTick()
      } catch (e: Exception) {
        try {
          Thread.sleep(1_000L)
        } catch (e: Exception) {
          // Don't care
        }
      }
    }
  }

  private fun opTick() {
    this.statusNow = when (val oldStatus = this.statusNow) {
      is Paused, is Stopped -> oldStatus

      is Running -> {
        when (val c = oldStatus.configuration) {
          EndOfChapter, Off -> oldStatus

          is WithDuration -> {
            val completed =
              (c.duration <= oneSecond)

            val newStatus: Status =
              if (completed) {
                Stopped(configuration = Off)
              } else {
                val newDuration =
                  c.duration.minus(oneSecond)
                val newConfiguration =
                  WithDuration(newDuration)
                oldStatus.copy(configuration = newConfiguration)
              }

            this.eventsSubject.onNext(PlayerSleepTimerStatusChanged(oldStatus, newStatus))
            if (completed) {
              this.eventsSubject.onNext(PlayerSleepTimerFinished)
            }
            newStatus
          }
        }
      }
    }
  }

  private fun opStart() {
    this.statusNow = when (val oldStatus = this.statusNow) {
      is Running -> oldStatus
      is Paused, is Stopped -> {
        val newStatus = Running(configuration = oldStatus.configuration)
        this.eventsSubject.onNext(PlayerSleepTimerStatusChanged(oldStatus, newStatus))
        newStatus
      }
    }
  }

  private fun opCancel() {
    this.statusNow = when (val oldStatus = this.statusNow) {
      is Paused, is Stopped -> oldStatus

      is Running -> {
        val newStatus = Stopped(configuration = Off)
        this.eventsSubject.onNext(PlayerSleepTimerStatusChanged(oldStatus, newStatus))
        newStatus
      }
    }
  }

  private fun opReconfigure(newConfiguration: PlayerSleepTimerConfiguration) {
    this.statusNow = when (val oldStatus = this.statusNow) {
      is Paused -> {
        val newStatus = oldStatus.copy(configuration = newConfiguration)
        this.eventsSubject.onNext(PlayerSleepTimerStatusChanged(oldStatus, newStatus))
        newStatus
      }

      is Running -> {
        val newStatus = oldStatus.copy(configuration = newConfiguration)
        this.eventsSubject.onNext(PlayerSleepTimerStatusChanged(oldStatus, newStatus))
        newStatus
      }

      is Stopped -> {
        val newStatus = oldStatus.copy(configuration = newConfiguration)
        this.eventsSubject.onNext(PlayerSleepTimerStatusChanged(oldStatus, newStatus))
        newStatus
      }
    }
  }

  private fun onFinish() {
    val oldStatus = this.statusNow
    val newStatus = Stopped(configuration = Off)
    this.statusNow = newStatus
    this.eventsSubject.onNext(PlayerSleepTimerStatusChanged(oldStatus, newStatus))
    this.eventsSubject.onNext(PlayerSleepTimerFinished)
  }

  private fun opUnpause() {
    this.statusNow = when (val oldStatus = this.statusNow) {
      is Paused -> {
        val newStatus = Running(configuration = oldStatus.configuration)
        this.eventsSubject.onNext(PlayerSleepTimerStatusChanged(oldStatus, newStatus))
        newStatus
      }

      is Stopped, is Running -> oldStatus
    }
  }

  private fun opPause() {
    val oldStatus = this.statusNow
    val newStatus = Paused(configuration = oldStatus.configuration)
    this.eventsSubject.onNext(PlayerSleepTimerStatusChanged(oldStatus, newStatus))
    this.statusNow = newStatus
  }

  override fun start() {
    this.commandQueue.add(Start)
  }

  override fun configure(configuration: PlayerSleepTimerConfiguration) {
    this.commandQueue.add(Reconfigure(configuration))
  }

  override fun cancel() {
    this.commandQueue.add(Cancel)
  }

  override fun pause() {
    this.commandQueue.add(Pause)
  }

  override fun unpause() {
    this.commandQueue.add(Unpause)
  }

  override fun finish() {
    this.commandQueue.add(Finish)
  }

  override val events: Observable<PlayerSleepTimerEvent>
    get() = this.eventsOnUI

  override val configuration: PlayerSleepTimerConfiguration
    get() = this.statusNow.configuration

  override val status: Status
    get() = this.statusNow
}
