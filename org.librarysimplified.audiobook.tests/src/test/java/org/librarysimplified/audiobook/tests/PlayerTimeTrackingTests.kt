package org.librarysimplified.audiobook.tests

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.librarysimplified.audiobook.time_tracking.PlayerTimeTracked
import org.librarysimplified.audiobook.time_tracking.PlayerTimeTracker
import org.librarysimplified.audiobook.time_tracking.PlayerTimeTrackerType
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

@Timeout(30L, unit = TimeUnit.SECONDS)
class PlayerTimeTrackingTests {

  private val logger =
    LoggerFactory.getLogger(PlayerTimeTrackingTests::class.java)

  private lateinit var tracker: PlayerTimeTrackerType
  private lateinit var timeSegments: ConcurrentLinkedQueue<SegmentReceived>
  private lateinit var clock: FakeClock

  class FakeClock {

    private val logger =
      LoggerFactory.getLogger(FakeClock::class.java)

    @Volatile
    var timeNow: OffsetDateTime = OffsetDateTime.now()
      set(newTime) {
        field = newTime
        this.logger.debug("Time is now {}", newTime)
      }

    fun now(): OffsetDateTime {
      return this.timeNow
    }
  }

  data class SegmentReceived(
    val time: OffsetDateTime,
    val segment: PlayerTimeTracked
  )

  @BeforeEach
  fun setup() {
    this.clock = FakeClock()
    this.clock.timeNow = OffsetDateTime.now()

    this.timeSegments =
      ConcurrentLinkedQueue<SegmentReceived>()
    this.tracker =
      PlayerTimeTracker.create(
        frequency = Duration.ofMillis(1L),
        clock = { this.clock.now() }
      )

    this.tracker.timeSegments.subscribe { t ->
      this.logger.debug("[{}]: Received segment {}", OffsetDateTime.now(), t)
      this.timeSegments.add(
        SegmentReceived(
          time = OffsetDateTime.now(),
          segment = t
        )
      )
    }
  }

  @AfterEach
  fun tearDown() {
    this.tracker.close()

    /*
     * We count the number of ticks the tracker experienced as a paranoid check; the number
     * should be incremented every command iteration, so if the number of ticks is low, then
     * perhaps the tracker never actually ran any commands…
     */

    val debugTicks = (this.tracker as PlayerTimeTracker).debugTicks()
    assertTrue(debugTicks >= 3L, "Debug ticks ${debugTicks} must be >= 3L")
  }

  /**
   * Opening a book and doing nothing publishes nothing, even if multiple minutes pass.
   */

  @Test
  fun testNoSegmentsPublished() {
    this.tracker.bookOpened("ABCD").join()
    this.clock.timeNow = this.clock.timeNow.plusMinutes(2L)
    this.tracker.bookClosed().join()
    this.tracker.close()

    assertEquals(0, this.timeSegments.size)
    assertThrows<IllegalStateException> { this.tracker.bookClosed() }
  }

  /**
   * Opening a book, starting playback, and then stopping playback two and a half minutes later
   * publishes three segments.
   */

  @Test
  fun testPlaybackPublishSimple() {
    this.tracker.bookOpened("ABCD").join()
    this.tracker.bookPlaybackStarted("ABCD", 1.0).join()
    this.clock.timeNow = this.clock.timeNow.plusSeconds((2 * 60) + 30)
    this.tracker.bookPlaybackPaused("ABCD", 1.0).join()
    this.tracker.bookClosed().join()
    this.tracker.close()

    assertEquals(3, this.timeSegments.size)
    assertEquals((2 * 60) + 30, this.timeSegments.sumOf { t -> t.segment.duration().toSeconds() })
    assertEquals(3, this.timeSegments.map { t -> t.segment.id }.toSet().size)

    assertThrows<IllegalStateException> { this.tracker.bookClosed() }
  }

  /**
   * Opening a book, starting playback, and then stopping playback two and a half minutes later
   * publishes three segments.
   */

  @Test
  fun testPlaybackPublishSimpleWaiting() {
    this.tracker.bookOpened("ABCD").join()
    this.tracker.bookPlaybackStarted("ABCD", 1.0).join()
    this.clock.timeNow = this.clock.timeNow.plusSeconds(60)
    Thread.sleep(1_000L)
    this.clock.timeNow = this.clock.timeNow.plusSeconds(60)
    Thread.sleep(1_000L)
    this.clock.timeNow = this.clock.timeNow.plusSeconds(30)
    Thread.sleep(1_000L)
    this.tracker.close()

    assertEquals(3, this.timeSegments.size)
    assertEquals((2 * 60) + 30, this.timeSegments.sumOf { t -> t.segment.duration().toSeconds() })
    assertEquals(3, this.timeSegments.map { t -> t.segment.id }.toSet().size)

    assertThrows<IllegalStateException> { this.tracker.bookClosed() }
  }

  /**
   * Playback rate changes trigger segment publication.
   */

  @Test
  fun testPlaybackRateChanges() {
    this.tracker.bookOpened("ABCD").join()
    this.tracker.bookPlaybackStarted("ABCD", 1.0).join()
    this.clock.timeNow = this.clock.timeNow.plusSeconds(60)
    this.tracker.bookPlaybackRateChanged("ABCD", 0.5).join()
    this.clock.timeNow = this.clock.timeNow.plusSeconds(60)
    this.tracker.bookPlaybackRateChanged("ABCD", 0.5).join()
    this.clock.timeNow = this.clock.timeNow.plusSeconds(30)
    this.tracker.bookPlaybackRateChanged("ABCD", 0.5).join()
    this.tracker.close()

    assertEquals(3, this.timeSegments.size)
    assertEquals((2 * 60) + 30, this.timeSegments.sumOf { t -> t.segment.duration().toSeconds() })
    assertEquals(3, this.timeSegments.map { t -> t.segment.id }.toSet().size)

    assertThrows<IllegalStateException> { this.tracker.bookClosed() }
  }
}
