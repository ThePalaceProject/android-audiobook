package org.librarysimplified.audiobook.tests

import org.joda.time.Duration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent.PlayerSleepTimerCancelled
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent.PlayerSleepTimerFinished
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent.PlayerSleepTimerRunning
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent.PlayerSleepTimerStopped
import org.librarysimplified.audiobook.api.PlayerSleepTimerType
import org.slf4j.Logger
import java.util.concurrent.CountDownLatch

/**
 * Test contract for the {@link org.librarysimplified.audiobook.api.PlayerSleepTimerType} interface.
 */

abstract class PlayerSleepTimerContract {

  abstract fun create(): PlayerSleepTimerType

  abstract fun logger(): Logger

  /**
   * Opening a timer and then closing it works. Closing it multiple times isn't an issue.
   */

  @Test
  fun testOpenClose() {
    val timer = this.create()
    Assertions.assertFalse(timer.isClosed, "Timer not closed")
    timer.close()
    Assertions.assertTrue(timer.isClosed, "Timer is closed")
    timer.close()
    Assertions.assertTrue(timer.isClosed, "Timer is closed")
  }

  /**
   * Opening a timer, starting it, and letting it count down to completion works.
   */

  @Test
  @Timeout(10)
  fun testCountdown() {
    val logger = this.logger()
    val timer = this.create()

    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()

    timer.status.subscribe(
      { event ->
        logger.debug("event: {}", event)
        events.add(
          when (event) {
            PlayerSleepTimerStopped -> "stopped"
            is PlayerSleepTimerRunning -> "running"
            is PlayerSleepTimerCancelled -> "cancelled"
            PlayerSleepTimerFinished -> "finished"
          }
        )
      },
      { waitLatch.countDown() },
      { waitLatch.countDown() }
    )

    logger.debug("starting timer")
    timer.start(Duration.millis(3000L))

    logger.debug("waiting for timer")
    Thread.sleep(1000L)
    Assertions.assertNotNull(timer.isRunning)
    Thread.sleep(1000L)
    Thread.sleep(1000L)
    Thread.sleep(1000L)

    logger.debug("closing timer")
    timer.close()

    waitLatch.await()

    logger.debug("events: {}", events)
    Assertions.assertEquals(7, events.size)
    Assertions.assertEquals("stopped", events[0])
    Assertions.assertEquals("running", events[1])
    Assertions.assertEquals("running", events[2])
    Assertions.assertEquals("running", events[3])
    Assertions.assertEquals("running", events[4])
    Assertions.assertEquals("finished", events[5])
    Assertions.assertEquals("stopped", events[6])
  }

  /**
   * Opening a timer, starting it, and then cancelling it, works.
   */

  @Test
  @Timeout(10)
  fun testCancel() {

    val logger = this.logger()
    val timer = this.create()

    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()

    timer.status.subscribe(
      { event ->
        logger.debug("event: {}", event)
        events.add(
          when (event) {
            PlayerSleepTimerStopped -> "stopped"
            is PlayerSleepTimerRunning -> "running"
            is PlayerSleepTimerCancelled -> "cancelled"
            PlayerSleepTimerFinished -> "finished"
          }
        )
      },
      { waitLatch.countDown() },
      { waitLatch.countDown() }
    )

    logger.debug("starting timer")
    timer.start(Duration.millis(3000L))

    logger.debug("waiting for timer")
    Thread.sleep(1000L)
    Assertions.assertNotNull(timer.isRunning)

    logger.debug("cancelling timer")
    timer.cancel()

    logger.debug("waiting for timer")
    Thread.sleep(1000L)
    Assertions.assertNull(timer.isRunning)

    logger.debug("closing timer")
    timer.close()

    waitLatch.await()

    logger.debug("events: {}", events)
    Assertions.assertTrue(events.size >= 4, "Must receive at least 4 events")
    Assertions.assertEquals("stopped", events.first())
    Assertions.assertTrue(events.contains("cancelled"), "Received at least a cancelled event")
    Assertions.assertTrue(events.contains("running"), "Received at least a running event")
    Assertions.assertEquals("stopped", events.last())
  }

  /**
   * Opening a timer, starting it, and then cancelling it, works.
   */

  @Test
  @Timeout(10)
  fun testCancelImmediate() {
    val logger = this.logger()
    val timer = this.create()

    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()

    timer.status.subscribe(
      { event ->
        logger.debug("event: {}", event)
        events.add(
          when (event) {
            PlayerSleepTimerStopped -> "stopped"
            is PlayerSleepTimerRunning -> "running"
            is PlayerSleepTimerCancelled -> "cancelled"
            PlayerSleepTimerFinished -> "finished"
          }
        )
      },
      { waitLatch.countDown() },
      { waitLatch.countDown() }
    )

    logger.debug("starting timer")
    timer.start(Duration.millis(3000L))

    logger.debug("cancelling timer")
    timer.cancel()
    Thread.sleep(250L)
    Assertions.assertNull(timer.isRunning)

    logger.debug("closing timer")
    timer.close()

    waitLatch.await()

    logger.debug("events: {}", events)
    Assertions.assertTrue(events.size >= 1, "Must have received at least one events")
    Assertions.assertEquals("stopped", events.first())

    /*
     * This is timing sensitive. We may not receive a cancelled event if the timer doesn't even
     * have time to start.
     */

    if (events.size >= 4) {
      Assertions.assertTrue(events.contains("running"), "Received at least a running event")
    }
    if (events.size >= 3) {
      Assertions.assertTrue(events.contains("cancelled"), "Received at least a cancelled event")
    }

    Assertions.assertEquals("stopped", events.last())
  }

  /**
   * Opening a timer, starting it, and then restarting it with a new time, works.
   */

  @Test
  @Timeout(10)
  fun testRestart() {
    val events = ArrayList<String>()

    val logger = this.logger()
    val timer = this.create()

    timer.status.subscribe { event ->
      logger.debug("event: {}", event)

      events.add(
        when (event) {
          PlayerSleepTimerStopped -> "stopped"
          is PlayerSleepTimerRunning -> "running " + event.remaining
          is PlayerSleepTimerCancelled -> "cancelled"
          PlayerSleepTimerFinished -> "finished"
        }
      )
    }

    logger.debug("starting timer")
    timer.start(Duration.millis(4000L))

    logger.debug("waiting for timer")
    Thread.sleep(1000L)

    logger.debug("restarting timer")
    timer.start(Duration.millis(6000L))

    logger.debug("waiting for timer")
    Thread.sleep(1000L)
    Assertions.assertNotNull(timer.isRunning)

    logger.debug("closing timer")
    timer.close()
    Thread.sleep(1000L)

    logger.debug("events: {}", events)
    Assertions.assertTrue(events.size >= 4, "Must have received at least 4 events")
    Assertions.assertEquals("stopped", events.first())
    Assertions.assertTrue(events.contains("running PT4S"))
    Assertions.assertTrue(events.contains("running PT6S"))
    Assertions.assertEquals("stopped", events.last())
  }

  /**
   * Running the timer to completion repeatedly, works.
   */

  @Test
  @Timeout(10)
  fun testCompletionRepeated() {
    val logger = this.logger()
    val timer = this.create()

    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()

    timer.status.subscribe(
      { event ->
        logger.debug("event: {}", event)
        events.add(
          when (event) {
            PlayerSleepTimerStopped -> "stopped"
            is PlayerSleepTimerRunning -> "running " + event.remaining
            is PlayerSleepTimerCancelled -> "cancelled"
            PlayerSleepTimerFinished -> "finished"
          }
        )
      },
      { waitLatch.countDown() },
      { waitLatch.countDown() }
    )

    logger.debug("starting timer")
    timer.start(Duration.millis(1000L))

    logger.debug("waiting for timer")
    Thread.sleep(2000L)

    logger.debug("restarting timer")
    timer.start(Duration.millis(1000L))

    logger.debug("waiting for timer")
    Thread.sleep(2000L)

    logger.debug("closing timer")
    timer.close()

    waitLatch.await()

    logger.debug("events: {}", events)
    Assertions.assertEquals(8, events.size, "Must have received 8 events")
    Assertions.assertEquals("stopped", events[0])
    Assertions.assertEquals("running PT1S", events[1])
    Assertions.assertEquals("running PT0S", events[2])
    Assertions.assertEquals("finished", events[3])
    Assertions.assertEquals("running PT1S", events[4])
    Assertions.assertEquals("running PT0S", events[5])
    Assertions.assertEquals("finished", events[6])
    Assertions.assertEquals("stopped", events[7])
  }

  /**
   * Explicit completion works.
   */

  @Test
  @Timeout(10)
  fun testCompletionIndefinite() {
    val logger = this.logger()
    val timer = this.create()

    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()

    timer.status.subscribe(
      { event ->
        logger.debug("event: {}", event)
        events.add(
          when (event) {
            PlayerSleepTimerStopped -> "stopped"
            is PlayerSleepTimerRunning -> "running " + event.remaining
            is PlayerSleepTimerCancelled -> "cancelled"
            PlayerSleepTimerFinished -> "finished"
          }
        )
      },
      { waitLatch.countDown() },
      { waitLatch.countDown() }
    )

    logger.debug("starting timer")
    timer.start(null)

    logger.debug("waiting for timer")
    Thread.sleep(1000L)

    logger.debug("finishing timer")
    timer.finish()

    logger.debug("waiting for timer")
    Thread.sleep(1000L)

    logger.debug("finishing timer")
    timer.finish()

    logger.debug("waiting for timer")
    Thread.sleep(1000L)
    Assertions.assertNull(timer.isRunning)

    timer.close()

    waitLatch.await()

    logger.debug("events: {}", events)
    Assertions.assertEquals(4, events.size, "Must have received 4 events")
    Assertions.assertEquals("stopped", events[0])
    Assertions.assertEquals("running null", events[1])
    Assertions.assertEquals("finished", events[2])
    Assertions.assertEquals("stopped", events[3])
  }

  /**
   * Explicit completion works.
   */

  @Test
  @Timeout(10)
  fun testCompletionTimed() {
    val logger = this.logger()
    val timer = this.create()

    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()

    timer.status.subscribe(
      { event ->
        logger.debug("event: {}", event)
        events.add(
          when (event) {
            PlayerSleepTimerStopped -> "stopped"
            is PlayerSleepTimerRunning -> "running " + event.remaining
            is PlayerSleepTimerCancelled -> "cancelled"
            PlayerSleepTimerFinished -> "finished"
          }
        )
      },
      { waitLatch.countDown() },
      { waitLatch.countDown() }
    )

    logger.debug("starting timer")
    timer.start(Duration.standardSeconds(2L))

    logger.debug("waiting for timer")
    Thread.sleep(500L)

    logger.debug("finishing timer")
    timer.finish()

    logger.debug("waiting for timer")
    Thread.sleep(1000L)

    timer.close()

    waitLatch.await()

    logger.debug("events: {}", events)
    Assertions.assertEquals(4, events.size, "Must have received 4 events")
    Assertions.assertEquals("stopped", events[0])
    Assertions.assertEquals("running PT2S", events[1])
    Assertions.assertEquals("finished", events[2])
    Assertions.assertEquals("stopped", events[3])
  }

  /**
   * Pausing a timer works.
   */

  @Test
  @Timeout(10)
  fun testPause() {
    val logger = this.logger()
    val timer = this.create()

    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()

    timer.status.subscribe(
      { event ->
        logger.debug("event: {}", event)
        events.add(
          when (event) {
            PlayerSleepTimerStopped -> "stopped"
            is PlayerSleepTimerRunning -> "running" + (if (event.paused) " paused" else "")
            is PlayerSleepTimerCancelled -> "cancelled"
            PlayerSleepTimerFinished -> "finished"
          }
        )
      },
      { waitLatch.countDown() },
      { waitLatch.countDown() }
    )

    logger.debug("starting timer")
    timer.start(Duration.millis(3000L))

    logger.debug("waiting for timer")
    Thread.sleep(1000L)

    timer.pause()
    Thread.sleep(1000L)
    val running = timer.isRunning!!
    Assertions.assertTrue(running.paused, "Is paused")

    Thread.sleep(1000L)
    Thread.sleep(1000L)

    logger.debug("closing timer")
    timer.close()

    waitLatch.await()

    logger.debug("events: {}", events)
    val distinctEvents = withoutSuccessiveDuplicates(events)
    logger.debug("distinctEvents: {}", distinctEvents)

    logger.debug("events: {}", events)
    Assertions.assertEquals(4, distinctEvents.size)
    Assertions.assertEquals("stopped", distinctEvents[0])
    Assertions.assertEquals("running", distinctEvents[1])
    Assertions.assertEquals("running paused", distinctEvents[2])
    Assertions.assertEquals("stopped", distinctEvents[3])
  }

  /**
   * Pausing and unpausing a timer works.
   */

  @Test
  @Timeout(10)
  fun testUnpause() {
    val logger = this.logger()
    val timer = this.create()

    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()

    timer.status.subscribe(
      { event ->
        logger.debug("event: {}", event)
        events.add(
          when (event) {
            PlayerSleepTimerStopped -> "stopped"
            is PlayerSleepTimerRunning -> "running" + (if (event.paused) " paused" else "")
            is PlayerSleepTimerCancelled -> "cancelled"
            PlayerSleepTimerFinished -> "finished"
          }
        )
      },
      { waitLatch.countDown() },
      { waitLatch.countDown() }
    )

    logger.debug("starting timer")
    timer.start(Duration.millis(3000L))

    logger.debug("waiting for timer")
    timer.pause()

    Thread.sleep(1000L)
    val running = timer.isRunning!!
    Assertions.assertTrue(running.paused, "Is paused")

    Thread.sleep(1000L)

    timer.unpause()
    Thread.sleep(1000L)
    val stillRunning = timer.isRunning!!
    Assertions.assertFalse(stillRunning.paused, "Is not paused")

    Thread.sleep(1000L)
    Thread.sleep(1000L)
    Thread.sleep(1000L)
    Thread.sleep(1000L)

    logger.debug("closing timer")
    timer.close()

    waitLatch.await()

    logger.debug("events: {}", events)
    val distinctEvents = withoutSuccessiveDuplicates(events)
    logger.debug("distinctEvents: {}", distinctEvents)

    Assertions.assertEquals(6, distinctEvents.size)
    Assertions.assertEquals("stopped", distinctEvents[0])
    Assertions.assertEquals("running", distinctEvents[1])
    Assertions.assertEquals("running paused", distinctEvents[2])
    Assertions.assertEquals("running", distinctEvents[3])
    Assertions.assertEquals("finished", distinctEvents[4])
    Assertions.assertEquals("stopped", distinctEvents[5])
  }

  /**
   * Sending unpause requests to an unpaused timer is redundant.
   */

  @Test
  @Timeout(10)
  fun testUnpauseRedundant() {
    val logger = this.logger()
    val timer = this.create()

    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()

    timer.status.subscribe(
      { event ->
        logger.debug("event: {}", event)
        events.add(
          when (event) {
            PlayerSleepTimerStopped -> "stopped"
            is PlayerSleepTimerRunning -> "running" + (if (event.paused) " paused" else "")
            is PlayerSleepTimerCancelled -> "cancelled"
            PlayerSleepTimerFinished -> "finished"
          }
        )
      },
      { waitLatch.countDown() },
      { waitLatch.countDown() }
    )

    logger.debug("starting timer")
    timer.start(Duration.millis(3000L))

    logger.debug("waiting for timer")
    Thread.sleep(1000L)

    Thread.sleep(1000L)
    val running = timer.isRunning!!
    Assertions.assertFalse(running.paused, "Is paused")

    timer.unpause()
    Thread.sleep(1000L)
    val stillRunning = timer.isRunning!!
    Assertions.assertFalse(stillRunning.paused, "Is not paused")

    Thread.sleep(1000L)
    Thread.sleep(1000L)
    Thread.sleep(1000L)

    logger.debug("closing timer")
    timer.close()

    waitLatch.await()

    logger.debug("events: {}", events)
    val distinctEvents = withoutSuccessiveDuplicates(events)
    logger.debug("distinctEvents: {}", distinctEvents)

    Assertions.assertEquals(4, distinctEvents.size)
    Assertions.assertEquals("stopped", distinctEvents[0])
    Assertions.assertEquals("running", distinctEvents[1])
    Assertions.assertEquals("finished", distinctEvents[2])
    Assertions.assertEquals("stopped", distinctEvents[3])
  }

  private fun <T> withoutSuccessiveDuplicates(values: List<T>): List<T> {
    var current: T? = null
    val results = ArrayList<T>()
    for (x in values) {
      if (x != current) {
        results.add(x)
        current = x
      }
    }
    return results
  }
}
