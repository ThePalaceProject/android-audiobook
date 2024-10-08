package org.librarysimplified.audiobook.time_tracking

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * The basic player time tracker service.
 */

class PlayerTimeTracker private constructor(
  val frequency: Duration,
  val clock: () -> OffsetDateTime
) : PlayerTimeTrackerType {

  private val oneMinuteMilliseconds =
    60L * 1000L

  sealed class Command {
    abstract val future: CompletableFuture<Void>

    data class ShutDown(
      override val future: CompletableFuture<Void>
    ) : Command()

    data class BookOpened(
      override val future: CompletableFuture<Void>,
      val trackingId: String
    ) : Command()

    data class BookClosed(
      override val future: CompletableFuture<Void>
    ) : Command()

    data class BookPlaybackStarted(
      override val future: CompletableFuture<Void>,
      val trackingId: String,
      val rate: Double
    ) : Command()

    data class BookPlaybackRateChanged(
      override val future: CompletableFuture<Void>,
      val trackingId: String,
      val rate: Double
    ) : Command()

    data class BookPlaybackPaused(
      override val future: CompletableFuture<Void>,
      val trackingId: String,
      val rate: Double
    ) : Command()
  }

  sealed class State {
    data object NoBook : State()

    data class Paused(
      val bookTrackingId: String,
      val rate: Double
    ) : State()

    data class Playing(
      val bookTrackingId: String,
      val startedAt: OffsetDateTime,
      val rate: Double
    ) : State()
  }

  private val logger =
    LoggerFactory.getLogger(PlayerTimeTracker::class.java)

  companion object {
    fun create(
      frequency: Duration,
      clock: () -> OffsetDateTime
    ): PlayerTimeTrackerType {
      return PlayerTimeTracker(frequency, clock)
    }

    fun create(
      clock: () -> OffsetDateTime
    ): PlayerTimeTrackerType {
      return this.create(Duration.ofSeconds(1L), clock)
    }

    fun create(): PlayerTimeTrackerType {
      return this.create { OffsetDateTime.now() }
    }
  }

  private val timeTrackedSubject =
    PublishSubject.create<PlayerTimeTracked>()
      .toSerialized()

  private val debugTicks =
    AtomicLong(0L)
  private val closeRequested =
    AtomicBoolean(false)
  private val closeCompleted =
    AtomicBoolean(false)
  private var state: State =
    State.NoBook
  private val commandQueue =
    LinkedBlockingQueue<Command>()

  private val executor: ExecutorService =
    Executors.newFixedThreadPool(1) { r ->
      val thread = Thread(r)
      thread.name = "org.librarysimplified.audiobook.time_tracking[${thread.id}]"
      thread.isDaemon = true
      thread.priority = Thread.MIN_PRIORITY
      thread
    }

  init {
    this.executor.execute(this::processCommands)
  }

  private fun processCommands() {
    this.logger.debug("Time tracker service started.")

    while (!this.closeCompleted.get()) {
      this.debugTicks.incrementAndGet()

      try {
        val command =
          this.commandQueue.poll(this.frequency.toMillis(), MILLISECONDS)

        when (command) {
          is Command.ShutDown -> {
            this.opShutdown()
          }
          is Command.BookOpened -> {
            this.opBookOpened(command)
          }
          is Command.BookPlaybackPaused -> {
            this.opBookPaused(command)
          }
          is Command.BookPlaybackRateChanged -> {
            this.opBookRateChanged(command)
          }
          is Command.BookPlaybackStarted -> {
            this.opBookStarted(command)
          }
          is Command.BookClosed -> {
            this.opBookClosed()
          }
          null -> {
            // Nothing to do.
          }
        }

        command?.future?.complete(null)

        when (val stateNow = this.state) {
          State.NoBook,
          is State.Paused -> {
            // Nothing to do
          }

          is State.Playing -> {
            val timeNow = this.clock.invoke()
            val timeThen = stateNow.startedAt
            if (Duration.between(timeThen, timeNow).toMillis() >= this.oneMinuteMilliseconds) {
              this.publishTimeSegments()
            }
          }
        }
      } catch (e: Throwable) {
        this.logger.debug("Exception during command processing: ", e)
        try {
          Thread.sleep(1_000L)
        } catch (e: Throwable) {
          // Ignored
        }
      }
    }
  }

  fun debugTicks(): Long {
    return this.debugTicks.get()
  }

  private fun opBookClosed() {
    this.logger.debug("Book closed")
    this.publishTimeSegments()
    this.state = State.NoBook
  }

  private fun opBookStarted(
    command: Command.BookPlaybackStarted
  ) {
    this.logger.debug("Book started ({})", command.trackingId)
    this.publishTimeSegments()
    this.state = State.Playing(
      bookTrackingId = command.trackingId,
      startedAt = this.clock.invoke(),
      rate = command.rate
    )
  }

  private fun opBookRateChanged(
    command: Command.BookPlaybackRateChanged
  ) {
    this.logger.debug("Book rate changed ({})", command.trackingId)
    this.publishTimeSegments()
    this.state = State.Playing(
      bookTrackingId = command.trackingId,
      startedAt = this.clock.invoke(),
      rate = command.rate
    )
  }

  private fun opBookPaused(
    command: Command.BookPlaybackPaused
  ) {
    this.logger.debug("Book paused ({})", command.trackingId)
    this.publishTimeSegments()
    this.state = State.Paused(
      bookTrackingId = command.trackingId,
      rate = command.rate
    )
  }

  private fun opBookOpened(
    command: Command.BookOpened
  ) {
    this.logger.debug("Book opened ({})", command.trackingId)
    this.publishTimeSegments()
    this.state = State.Paused(command.trackingId, rate = 1.0)
  }

  private fun publishTimeSegments() {
    return when (val stateNow = this.state) {
      State.NoBook,
      is State.Paused -> {
        // Nothing to publish
      }

      is State.Playing -> {
        this.logger.debug("Publishing time segments.")

        val timeNow = this.clock.invoke()
        var timeThen = stateNow.startedAt
        var count = 0

        while (Duration.between(timeThen, timeNow).toMillis() >= this.oneMinuteMilliseconds) {
          val timeNext = timeThen.plusMinutes(1L)
          this.publishTimeSegment(
            PlayerTimeTracked(
              id = UUID.randomUUID(),
              bookTrackingId = stateNow.bookTrackingId,
              timeStarted = timeThen,
              timeEnded = timeNext,
              rate = stateNow.rate
            )
          )
          ++count
          timeThen = timeNext
        }

        if (timeThen.isBefore(timeNow)) {
          this.publishTimeSegment(
            PlayerTimeTracked(
              id = UUID.randomUUID(),
              bookTrackingId = stateNow.bookTrackingId,
              timeStarted = timeThen,
              timeEnded = timeNow,
              rate = stateNow.rate
            )
          )
          ++count
        }

        this.state = stateNow.copy(startedAt = timeNow)
        this.logger.debug("Published {} time segments", count)
      }
    }
  }

  private fun publishTimeSegment(
    segment: PlayerTimeTracked
  ) {
    this.logger.debug("Publishing time segment.")
    this.timeTrackedSubject.onNext(segment)
  }

  private fun opShutdown() {
    try {
      this.logger.debug("Shutting down time tracker.")
      this.publishTimeSegments()
      this.timeTrackedSubject.onComplete()
    } finally {
      this.closeCompleted.set(true)
    }
  }

  override val timeSegments: Observable<PlayerTimeTracked> =
    this.timeTrackedSubject

  override fun bookOpened(bookTrackingId: String): CompletableFuture<Void> {
    this.checkNotClosed()
    val future = CompletableFuture<Void>()
    this.commandQueue.add(Command.BookOpened(future, bookTrackingId))
    return future
  }

  override fun bookClosed(): CompletableFuture<Void> {
    this.checkNotClosed()
    val future = CompletableFuture<Void>()
    this.commandQueue.add(Command.BookClosed(future))
    return future
  }

  override fun bookPlaybackStarted(
    bookTrackingId: String,
    rate: Double
  ): CompletableFuture<Void> {
    this.checkNotClosed()
    val future = CompletableFuture<Void>()
    this.commandQueue.add(Command.BookPlaybackStarted(future, bookTrackingId, rate))
    return future
  }

  override fun bookPlaybackRateChanged(
    bookTrackingId: String,
    rate: Double
  ): CompletableFuture<Void> {
    this.checkNotClosed()
    val future = CompletableFuture<Void>()
    this.commandQueue.add(Command.BookPlaybackRateChanged(future, bookTrackingId, rate))
    return future
  }

  override fun bookPlaybackPaused(
    bookTrackingId: String,
    rate: Double
  ): CompletableFuture<Void> {
    this.checkNotClosed()
    val future = CompletableFuture<Void>()
    this.commandQueue.add(Command.BookPlaybackPaused(future, bookTrackingId, rate))
    return future
  }

  private fun checkNotClosed() {
    if (this.closeRequested.get()) {
      throw IllegalStateException("Time tracking service is closed.")
    }
  }

  override fun close() {
    if (this.closeRequested.compareAndSet(false, true)) {
      val future = CompletableFuture<Void>()
      this.commandQueue.add(Command.ShutDown(future))
      this.executor.shutdown()
      this.executor.awaitTermination(30L, TimeUnit.SECONDS)
    }
  }
}
