package org.librarysimplified.audiobook.tests.open_access

import android.content.Context
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import org.joda.time.Duration
import org.joda.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerDownloadTaskType
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventPlaybackRateChanged
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventChapterCompleted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventChapterWaiting
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventCreateBookmark
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackBuffering
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackPaused
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackProgressUpdate
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackStarted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackStopped
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithSpineElement.PlayerEventPlaybackWaitingForAction
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloadExpired
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloadFailed
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloaded
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloading
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementNotDownloaded
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.audiobook.manifest.api.PlayerManifestMetadata
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.open_access.ExoEngineProvider
import org.librarysimplified.audiobook.open_access.ExoEngineThread
import org.librarysimplified.audiobook.open_access.ExoSpineElement
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.librarysimplified.audiobook.tests.DishonestDownloadProvider
import org.librarysimplified.audiobook.tests.FailingDownloadProvider
import org.librarysimplified.audiobook.tests.ResourceDownloadProvider
import org.slf4j.Logger
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/**
 * Tests for the {@link org.librarysimplified.audiobook.open_access.ExoEngineProvider} type.
 */

abstract class ExoEngineProviderContract {

  abstract fun log(): Logger

  abstract fun context(): Context

  /**
   * Check to see if the current test is executing as an instrumented test on a hardware or
   * emulated device, or if it's running as a local unit test with mocked Android components.
   *
   * @return `true` if the current test is running on a hardware or emulated device
   */

  abstract fun onRealDevice(): Boolean

  private lateinit var exec: ListeningExecutorService
  private lateinit var timeThen: Instant
  private lateinit var timeNow: Instant

  @BeforeEach
  open fun setup() {
    this.log().debug("setup")
    this.timeThen = Instant.now()
    this.exec = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1))!!
  }

  @AfterEach
  fun tearDown() {
    this.log().debug("tearDown")
    this.exec.shutdown()
    this.timeNow = Instant.now()
    this.log().debug("time: {}", Duration(this.timeThen, this.timeNow).standardSeconds)
  }

  /**
   * Test that the engine accepts the minimal example book.
   */

  @Test
  fun testAudioEnginesTrivial() {
    val manifest = this.parseManifest("ok_minimal_0.json")
    val request = PlayerAudioEngineRequest(
      manifest = manifest,
      filter = { true },
      downloadProvider = DishonestDownloadProvider(),
      userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
    )
    val engine_provider = ExoEngineProvider()
    val book_provider = engine_provider.tryRequest(request)
    Assertions.assertNotNull(book_provider, "Engine must handle manifest")
    val book_provider_nn = book_provider!!
    val result = book_provider_nn.create(this.context())
    this.log().debug("testAudioEnginesTrivial:result: {}", result)
    Assertions.assertTrue(result is PlayerResult.Success, "Engine accepts book")
  }

  /**
   * Test that the engine accepts the flatland book.
   */

  @Test
  fun testAudioEnginesFlatland() {
    val manifest = this.parseManifest("flatland.audiobook-manifest.json")
    val request = PlayerAudioEngineRequest(
      manifest = manifest,
      filter = { true },
      downloadProvider = DishonestDownloadProvider(),
      userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
    )
    val engine_provider = ExoEngineProvider()
    val book_provider = engine_provider.tryRequest(request)
    Assertions.assertNotNull(book_provider, "Engine must handle manifest")
    val book_provider_nn = book_provider!!
    val result = book_provider_nn.create(this.context())
    this.log().debug("testAudioEnginesFlatland:result: {}", result)
    Assertions.assertTrue(result is PlayerResult.Success, "Engine accepts book")
  }

  /**
   * Test that the engine accepts the Summer Wives (Feedbooks) book.
   */

  @Test
  fun testAudioEnginesFeedbooks() {
    val manifest = this.parseManifest("summerwives.audiobook-manifest.json")
    val request = PlayerAudioEngineRequest(
      manifest = manifest,
      filter = { true },
      downloadProvider = DishonestDownloadProvider(),
      userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
    )
    val engine_provider = ExoEngineProvider()
    val book_provider = engine_provider.tryRequest(request)
    Assertions.assertNotNull(book_provider, "Engine must handle manifest")
    val book_provider_nn = book_provider!!
    val result = book_provider_nn.create(this.context())
    this.log().debug("testAudioEnginesFeedbooks:result: {}", result)
    Assertions.assertTrue(result is PlayerResult.Success, "Engine accepts book")
  }

  /**
   * Test that the engine rejects the Best New Horror (LCP) book.
   */

  @Test
  fun lcpManifest_isRejected() {
    val manifest = this.parseManifest("bestnewhorror.audiobook-manifest.json")
    val request = PlayerAudioEngineRequest(
      manifest = manifest,
      filter = { true },
      downloadProvider = DishonestDownloadProvider(),
      userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
    )
    val engine_provider = ExoEngineProvider()
    val book_provider = engine_provider.tryRequest(request)
    Assertions.assertNull(book_provider, "Engine must reject manifest")
  }

  /**
   * Test that the player does not support streaming.
   */

  @Test
  fun testPlayNoStreaming() {
    val book = this.createBook("ok_minimal_0.json")
    Assertions.assertFalse(book.supportsStreaming, "Player does not support streaming")
  }

  /**
   * Test that the player reports closure.
   */

  @Test
  fun testPlayerOpenClose() {
    Assumptions.assumeTrue(this.onRealDevice(), "Test is running on a real device")

    val book = this.createBook("ok_minimal_0.json")

    val player = book.createPlayer()
    Assertions.assertFalse(player.isClosed, "Player is open")
    player.close()
    Assertions.assertTrue(player.isClosed, "Player is closed")
  }

  /**
   * Test that trying to play a closed player is an error.
   */

  @Test
  fun testPlayerClosedPlay() {
    Assumptions.assumeTrue(this.onRealDevice(), "Test is running on a real device")

    val book = this.createBook("ok_minimal_0.json")

    val player = book.createPlayer()
    Assertions.assertFalse(player.isClosed, "Player is open")
    player.close()

    Assertions.assertThrows(IllegalStateException::class.java) {
      player.play()
    }
  }

  /**
   * Test that if a spine element isn't downloaded, and a request is made to play that spine
   * element, the player will publish a "waiting" event and stop playback.
   */

  @Test
  @Timeout(20)
  fun testPlayerWaitingForChapter() {
    Assumptions.assumeTrue(this.onRealDevice(), "Test is running on a real device")

    val book = this.createBook("ok_minimal_0.json")

    val player = book.createPlayer()
    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()
    this.subscribeToEvents(player, events, waitLatch)

    player.play()
    player.close()
    waitLatch.await()

    this.showEvents(events)
    Assertions.assertEquals(2, events.size)
    Assertions.assertEquals("playbackChapterWaiting 0", events[0])
    Assertions.assertEquals("playbackStopped 0 0", events[1])
  }

  /**
   * Test that if the player is waiting for a particular spine element to be downloaded, that it
   * starts playback when the spine element becomes available.
   */

  @Test
  @Timeout(20)
  fun testPlayerPlayWhenDownloaded() {
    Assumptions.assumeTrue(this.onRealDevice(), "Test is running on a real device")

    val book =
      this.createBook(
        "flatland.audiobook-manifest.json",
        ResourceDownloadProvider.create(
          this.exec,
          mapOf(
            Pair(
              URI.create("http://www.archive.org/download/flatland_rg_librivox/flatland_1_abbott.mp3"),
              { this.resourceStream("noise.mp3") }
            )
          )
        )
      )

    val player = book.createPlayer()
    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()
    this.subscribeToEvents(player, events, waitLatch)

    book.downloadTasks.first().delete()
    Thread.sleep(1000L)

    player.play()
    this.downloadSpineItemAndWait(book.downloadTasks.first())
    Thread.sleep(1000L)

    player.close()
    waitLatch.await()

    this.showEvents(events)
    Assertions.assertEquals(7, events.size)
    Assertions.assertEquals("playbackChapterWaiting 0", events[0])
    Assertions.assertEquals("playbackStopped 0 0", events[1])
    Assertions.assertEquals("rateChanged NORMAL_TIME", events[2])
    Assertions.assertEquals("playbackStarted 0 0", events[3])
    Assertions.assertTrue(events[4].startsWith("playbackProgressUpdate 0 "), events[4])
    Assertions.assertTrue(events[5].startsWith("playbackProgressUpdate 0 "), events[5])
    Assertions.assertEquals("playbackStopped 0 0", events[6])
  }

  private fun subscribeToEvents(
    player: PlayerType,
    events: ArrayList<String>,
    waitLatch: CountDownLatch
  ) {
    player.events.subscribe(
      { event -> events.add(this.eventToString(event)) },
      { waitLatch.countDown() },
      { waitLatch.countDown() }
    )
  }

  /**
   * Test that the player can play downloaded spine elements.
   */

  @Test
  @Timeout(20)
  fun testPlayerPlayAlreadyDownloaded() {
    Assumptions.assumeTrue(this.onRealDevice(), "Test is running on a real device")

    val book =
      this.createBook(
        "flatland.audiobook-manifest.json",
        ResourceDownloadProvider.create(
          this.exec,
          mapOf(
            Pair(
              URI.create("http://www.archive.org/download/flatland_rg_librivox/flatland_1_abbott.mp3"),
              { this.resourceStream("noise.mp3") }
            )
          )
        )
      )

    val player = book.createPlayer()
    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()
    this.subscribeToEvents(player, events, waitLatch)

    book.downloadTasks.first().delete()
    Thread.sleep(1000L)

    this.downloadSpineItemAndWait(book.downloadTasks.first())
    Thread.sleep(1000L)

    player.play()
    Thread.sleep(1000L)

    player.close()
    waitLatch.await()

    this.showEvents(events)
    Assertions.assertEquals(3, events.size)
    Assertions.assertEquals("rateChanged NORMAL_TIME", events[0])
    Assertions.assertEquals("playbackStarted 0 0", events[1])
    Assertions.assertEquals("playbackStopped 0 0", events[2])
  }

  /**
   * Test that the player can play and pause.
   */

  @Test
  @Timeout(20)
  fun testPlayerPlayPausePlay() {
    Assumptions.assumeTrue(this.onRealDevice(), "Test is running on a real device")

    val book =
      this.createBook(
        "flatland.audiobook-manifest.json",
        ResourceDownloadProvider.create(
          this.exec,
          mapOf(
            Pair(
              URI.create("http://www.archive.org/download/flatland_rg_librivox/flatland_1_abbott.mp3"),
              { this.resourceStream("noise.mp3") }
            )
          )
        )
      )

    val player = book.createPlayer()
    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()
    this.subscribeToEvents(player, events, waitLatch)

    book.downloadTasks.first().delete()
    Thread.sleep(1000L)

    this.downloadSpineItemAndWait(book.downloadTasks.first())
    Thread.sleep(1000L)

    player.play()
    Thread.sleep(250L)

    player.pause()
    Thread.sleep(250L)

    player.play()
    Thread.sleep(250L)

    player.close()
    waitLatch.await()

    this.showEvents(events)
    Assertions.assertEquals(5, events.size)
    Assertions.assertEquals("rateChanged NORMAL_TIME", events[0])
    Assertions.assertEquals("playbackStarted 0 0", events[1])
    Assertions.assertEquals("playbackPaused 0 0", events[2])
    Assertions.assertEquals("playbackStarted 0 0", events[3])
    Assertions.assertEquals("playbackStopped 0 0", events[4])
  }

  /**
   * Test that the player automatically progresses across chapters.
   */

  @Test
  @Timeout(40)
  fun testPlayerPlayNextChapter() {
    Assumptions.assumeTrue(this.onRealDevice(), "Test is running on a real device")

    val book =
      this.createBook(
        "flatland.audiobook-manifest.json",
        ResourceDownloadProvider.create(
          this.exec,
          mapOf(
            Pair(
              URI.create("http://www.archive.org/download/flatland_rg_librivox/flatland_1_abbott.mp3"),
              { this.resourceStream("noise.mp3") }
            ),
            Pair(
              URI.create("http://www.archive.org/download/flatland_rg_librivox/flatland_2_abbott.mp3"),
              { this.resourceStream("noise.mp3") }
            )
          )
        )
      )

    val player = book.createPlayer()
    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()
    this.subscribeToEvents(player, events, waitLatch)

    book.downloadTasks.first().delete()
    book.downloadTasks[1].delete()
    Thread.sleep(1000L)

    this.downloadSpineItemAndWait(book.downloadTasks.first())
    this.downloadSpineItemAndWait(book.downloadTasks[1])
    Thread.sleep(1000L)

    player.play()
    Thread.sleep(12_000L)

    player.close()
    waitLatch.await()

    this.showEvents(events)
    Assertions.assertTrue(events.size >= 15, "At least 15 events must be logged (${events.size})")
    Assertions.assertEquals("rateChanged NORMAL_TIME", events.removeAt(0))
    Assertions.assertEquals("playbackStarted 0 0", events.removeAt(0))
    while (events[0].startsWith("playbackProgressUpdate 0")) {
      events.removeAt(0)
    }
    Assertions.assertEquals("playbackChapterCompleted 0", events.removeAt(0))
    Assertions.assertEquals("playbackStopped 0 0", events.removeAt(0))

    Assertions.assertEquals("rateChanged NORMAL_TIME", events.removeAt(0))
    Assertions.assertEquals("playbackStarted 1 0", events.removeAt(0))
    while (events[0].startsWith("playbackProgressUpdate 1")) {
      events.removeAt(0)
    }
    Assertions.assertEquals("playbackChapterCompleted 1", events.removeAt(0))
    Assertions.assertEquals("playbackStopped 1 0", events.removeAt(0))

    Assertions.assertEquals("playbackChapterWaiting 2", events.removeAt(0))
    Assertions.assertEquals("playbackStopped 2 0", events.removeAt(0))
  }

  /**
   * Test that deleting a spine element in the middle of playback stops playback.
   */

  @Test
  @Timeout(20)
  fun testPlayerPlayDeletePlaying() {
    Assumptions.assumeTrue(this.onRealDevice(), "Test is running on a real device")

    val book =
      this.createBook(
        "flatland.audiobook-manifest.json",
        ResourceDownloadProvider.create(
          this.exec,
          mapOf(
            Pair(
              URI.create("http://www.archive.org/download/flatland_rg_librivox/flatland_1_abbott.mp3"),
              { this.resourceStream("noise.mp3") }
            )
          )
        )
      )

    val player = book.createPlayer()
    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()
    this.subscribeToEvents(player, events, waitLatch)

    book.downloadTasks.first().delete()
    Thread.sleep(1000L)

    this.downloadSpineItemAndWait(book.downloadTasks.first())
    Thread.sleep(1000L)

    player.play()
    Thread.sleep(1000L)
    book.downloadTasks.first().delete()
    Thread.sleep(2000L)

    player.close()
    waitLatch.await()

    this.showEvents(events)
    Assertions.assertEquals(3, events.size)
    Assertions.assertEquals("rateChanged NORMAL_TIME", events[0])
    Assertions.assertEquals("playbackStarted 0 0", events[1])
    Assertions.assertEquals("playbackStopped 0 0", events[2])
  }

  /**
   * Test that the skipping to the next chapter works.
   */

  @Test
  @Timeout(20)
  fun testPlayerSkipNext() {
    Assumptions.assumeTrue(this.onRealDevice(), "Test is running on a real device")

    val book =
      this.createBook(
        "flatland.audiobook-manifest.json",
        ResourceDownloadProvider.create(
          this.exec,
          mapOf(
            Pair(
              URI.create("http://www.archive.org/download/flatland_rg_librivox/flatland_1_abbott.mp3"),
              { this.resourceStream("noise.mp3") }
            ),
            Pair(
              URI.create("http://www.archive.org/download/flatland_rg_librivox/flatland_2_abbott.mp3"),
              { this.resourceStream("noise.mp3") }
            )
          )
        )
      )

    val player = book.createPlayer()
    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()
    this.subscribeToEvents(player, events, waitLatch)

    book.downloadTasks.first().delete()
    book.downloadTasks[1].delete()
    Thread.sleep(1000L)

    this.downloadSpineItemAndWait(book.downloadTasks.first())
    this.downloadSpineItemAndWait(book.downloadTasks[1])

    player.play()
    player.skipToNextChapter(5000L)
    Thread.sleep(10_000L)

    player.close()
    waitLatch.await()

    this.showEvents(events)
    Assertions.assertTrue(events.size >= 12, "At least 12 events must be logged (${events.size})")
    Assertions.assertEquals("rateChanged NORMAL_TIME", events.removeAt(0))
    Assertions.assertEquals("playbackStarted 0 0", events.removeAt(0))
    Assertions.assertEquals("playbackStopped 0 0", events.removeAt(0))

    Assertions.assertEquals("rateChanged NORMAL_TIME", events.removeAt(0))
    Assertions.assertEquals("playbackStarted 1 0", events.removeAt(0))
    while (events[0].startsWith("playbackProgressUpdate 1")) {
      events.removeAt(0)
    }

    Assertions.assertEquals("playbackChapterCompleted 1", events.removeAt(0))
    Assertions.assertEquals("playbackStopped 1 0", events.removeAt(0))

    Assertions.assertEquals("playbackChapterWaiting 2", events.removeAt(0))
    Assertions.assertEquals("playbackStopped 2 0", events.removeAt(0))
  }

  /**
   * Test that the skipping to the previous chapter works.
   */

  @Test
  @Timeout(20)
  fun testPlayerSkipPrevious() {
    Assumptions.assumeTrue(this.onRealDevice(), "Test is running on a real device")

    val book =
      this.createBook(
        "flatland.audiobook-manifest.json",
        ResourceDownloadProvider.create(
          this.exec,
          mapOf(
            Pair(
              URI.create("http://www.archive.org/download/flatland_rg_librivox/flatland_1_abbott.mp3"),
              { this.resourceStream("noise.mp3") }
            ),
            Pair(
              URI.create("http://www.archive.org/download/flatland_rg_librivox/flatland_2_abbott.mp3"),
              { this.resourceStream("noise.mp3") }
            )
          )
        )
      )

    val player = book.createPlayer()
    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()
    this.subscribeToEvents(player, events, waitLatch)

    book.downloadTasks.first().delete()
    book.downloadTasks[1].delete()
    Thread.sleep(1000L)

    this.downloadSpineItemAndWait(book.downloadTasks.first())
    this.downloadSpineItemAndWait(book.downloadTasks[1])
    Thread.sleep(1000L)

    player.playAtLocation(book.spine[1].position)
    player.skipToPreviousChapter(5000L)
    Thread.sleep(12_000L)

    player.close()
    waitLatch.await()

    this.showEvents(events)
    Assertions.assertTrue(events.size >= 16, "At least 16 events must be logged (${events.size})")
    Assertions.assertEquals("rateChanged NORMAL_TIME", events.removeAt(0))
    Assertions.assertEquals("playbackStarted 1 0", events.removeAt(0))
    Assertions.assertEquals("playbackStopped 1 0", events.removeAt(0))

    Assertions.assertEquals("rateChanged NORMAL_TIME", events.removeAt(0))
    Assertions.assertEquals("playbackStarted 0 0", events.removeAt(0))
    while (events[0].startsWith("playbackProgressUpdate 0")) {
      events.removeAt(0)
    }
    Assertions.assertEquals("playbackChapterCompleted 0", events.removeAt(0))
    Assertions.assertEquals("playbackStopped 0 0", events.removeAt(0))

    Assertions.assertEquals("rateChanged NORMAL_TIME", events.removeAt(0))
    Assertions.assertEquals("playbackStarted 1 0", events.removeAt(0))
    while (events[0].startsWith("playbackProgressUpdate 1")) {
      events.removeAt(0)
    }
    Assertions.assertEquals("playbackChapterCompleted 1", events.removeAt(0))
    Assertions.assertEquals("playbackStopped 1 0", events.removeAt(0))

    Assertions.assertEquals("playbackChapterWaiting 2", events.removeAt(0))
    Assertions.assertEquals("playbackStopped 2 0", events.removeAt(0))
  }

  /**
   * Test that playing a specific chapter works.
   */

  @Test
  @Timeout(20)
  fun testPlayerPlayAtLocation() {
    Assumptions.assumeTrue(this.onRealDevice(), "Test is running on a real device")

    val book =
      this.createBook(
        "flatland.audiobook-manifest.json",
        ResourceDownloadProvider.create(
          this.exec,
          mapOf(
            Pair(
              URI.create("http://www.archive.org/download/flatland_rg_librivox/flatland_1_abbott.mp3"),
              { this.resourceStream("noise.mp3") }
            ),
            Pair(
              URI.create("http://www.archive.org/download/flatland_rg_librivox/flatland_2_abbott.mp3"),
              { this.resourceStream("noise.mp3") }
            )
          )
        )
      )

    val player = book.createPlayer()
    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()
    this.subscribeToEvents(player, events, waitLatch)

    book.downloadTasks.first().delete()
    book.downloadTasks[1].delete()
    Thread.sleep(1000L)

    this.downloadSpineItemAndWait(book.downloadTasks.first())
    this.downloadSpineItemAndWait(book.downloadTasks[1])
    Thread.sleep(1000L)

    player.playAtLocation(book.spine[1].position)
    Thread.sleep(10_000L)

    player.close()
    waitLatch.await()

    this.showEvents(events)
    Assertions.assertTrue(events.size >= 9, "At least 9 events must be logged (${events.size})")
    Assertions.assertEquals("rateChanged NORMAL_TIME", events.removeAt(0))
    Assertions.assertEquals("playbackStarted 1 0", events.removeAt(0))
    while (events[0].startsWith("playbackProgressUpdate 1")) {
      events.removeAt(0)
    }
    Assertions.assertEquals("playbackChapterCompleted 1", events.removeAt(0))
    Assertions.assertEquals("playbackStopped 1 0", events.removeAt(0))

    Assertions.assertEquals("playbackChapterWaiting 2", events.removeAt(0))
    Assertions.assertEquals("playbackStopped 2 0", events.removeAt(0))
  }

  /**
   * Test that playing the start of the book works.
   */

  @Test
  @Timeout(20)
  fun testPlayerPlayAtBookStart() {
    Assumptions.assumeTrue(this.onRealDevice(), "Test is running on a real device")

    val book =
      this.createBook(
        "flatland.audiobook-manifest.json",
        ResourceDownloadProvider.create(
          this.exec,
          mapOf(
            Pair(
              URI.create("http://www.archive.org/download/flatland_rg_librivox/flatland_1_abbott.mp3"),
              { this.resourceStream("noise.mp3") }
            ),
            Pair(
              URI.create("http://www.archive.org/download/flatland_rg_librivox/flatland_2_abbott.mp3"),
              { this.resourceStream("noise.mp3") }
            )
          )
        )
      )

    val player = book.createPlayer()
    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()
    this.subscribeToEvents(player, events, waitLatch)

    book.downloadTasks.first().delete()
    book.downloadTasks[1].delete()
    Thread.sleep(1000L)

    this.downloadSpineItemAndWait(book.downloadTasks.first())
    this.downloadSpineItemAndWait(book.downloadTasks[1])
    Thread.sleep(1000L)

    player.playAtBookStart()
    Thread.sleep(10_000L)

    player.close()
    waitLatch.await()

    this.showEvents(events)
    Assertions.assertTrue(events.size >= 9, "At least 9 events must be logged (${events.size})")
    Assertions.assertEquals("rateChanged NORMAL_TIME", events.removeAt(0))
    Assertions.assertEquals("playbackStarted 0 0", events.removeAt(0))
    while (events[0].startsWith("playbackProgressUpdate 0")) {
      events.removeAt(0)
    }
    Assertions.assertEquals("playbackChapterCompleted 0", events.removeAt(0))
    Assertions.assertEquals("playbackStopped 0 0", events.removeAt(0))
  }

  private fun showEvents(events: ArrayList<String>) {
    val log = this.log()
    log.debug("event count: {}", events.size)
    for (i in 0 until events.size) {
      log.debug("events[{}]: {}", i, events[i])
    }
  }

  private fun downloadSpineItemAndWait(downloadTask: PlayerDownloadTaskType) {
    downloadTask.delete()
    downloadTask.fetch()

    var downloaded = false
    while (!downloaded) {
      val status = downloadTask.spineItems.first().downloadStatus
      this.log().debug("spine element status: {}", status)

      when (status) {
        is PlayerSpineElementDownloadExpired -> Unit
        is PlayerSpineElementNotDownloaded -> Unit
        is PlayerSpineElementDownloading -> Unit
        is PlayerSpineElementDownloaded -> downloaded = true
        is PlayerSpineElementDownloadFailed -> {
          this.log().error("error: ", status.exception)
          Assertions.fail("Failed: " + status.message)
        }
      }

      Thread.sleep(1000L)
    }
  }

  private fun eventToString(event: PlayerEvent): String {
    return when (event) {
      is PlayerEventPlaybackRateChanged ->
        "rateChanged ${event.rate}"
      is PlayerEventPlaybackStarted ->
        "playbackStarted ${event.spineElement.index} ${event.offsetMilliseconds}"
      is PlayerEventPlaybackBuffering ->
        "playbackBuffering ${event.spineElement.index} ${event.offsetMilliseconds}"
      is PlayerEventPlaybackProgressUpdate ->
        "playbackProgressUpdate ${event.spineElement.index} ${event.offsetMilliseconds} ${event.offsetMilliseconds}"
      is PlayerEventChapterCompleted ->
        "playbackChapterCompleted ${event.spineElement.index}"
      is PlayerEventChapterWaiting ->
        "playbackChapterWaiting ${event.spineElement.index}"
      is PlayerEventPlaybackWaitingForAction ->
        "playbackWaitingForAction ${event.spineElement.index} ${event.offsetMilliseconds}"
      is PlayerEventPlaybackPaused ->
        "playbackPaused ${event.spineElement.index} ${event.offsetMilliseconds}"
      is PlayerEventPlaybackStopped ->
        "playbackStopped ${event.spineElement.index} ${event.offsetMilliseconds}"
      is PlayerEvent.PlayerEventError ->
        "playbackError ${event.spineElement?.index} ${event.exception?.javaClass?.canonicalName} ${event.errorCode} ${event.offsetMilliseconds}"
      PlayerEvent.PlayerEventManifestUpdated ->
        "playerManifestUpdated"
      is PlayerEventCreateBookmark ->
        "playerCreateBookmark"
    }
  }

  private fun createBook(
    name: String,
    downloadProvider: PlayerDownloadProviderType = DishonestDownloadProvider()
  ): PlayerAudioBookType {

    val manifest = this.parseManifest(name)
    val request =
      PlayerAudioEngineRequest(
        manifest = manifest,
        filter = { true },
        downloadProvider = downloadProvider,
        userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
      )
    val engine_provider = ExoEngineProvider()
    val book_provider = engine_provider.tryRequest(request)
    Assertions.assertNotNull(book_provider, "Engine must handle manifest")
    val book_provider_nn = book_provider!!
    val result = book_provider_nn.create(this.context())
    this.log().debug("testAudioEnginesTrivial:result: {}", result)

    val book = (result as PlayerResult.Success).result
    return book
  }

  private fun parseManifest(file: String): PlayerManifest {
    val result =
      ManifestParsers.parse(
        uri = URI.create("urn:$file"),
        streams = this.resource(file),
        extensions = listOf()
      )
    this.log().debug("parseManifest: result: {}", result)
    Assertions.assertTrue(result is ParseResult.Success, "Result is success")
    val manifest = (result as ParseResult.Success).result
    return manifest
  }

  /**
   * Test that manifest reloading works.
   */

  @Test
  fun testManifestReloading() {
    val manifest0 =
      this.parseManifest("ok_minimal_0.json")
    val manifest1 =
      this.parseManifest("ok_minimal_1.json")

    val request =
      PlayerAudioEngineRequest(
        manifest = manifest0,
        filter = { true },
        downloadProvider = DishonestDownloadProvider(),
        userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
      )

    val engineProvider =
      ExoEngineProvider(threadFactory = ExoEngineThread.Companion::createWithoutPreparation)
    val bookProvider =
      engineProvider.tryRequest(request)!!
    val audioBook =
      (bookProvider.create(this.context()) as PlayerResult.Success).result

    Assertions.assertEquals(
      URI.create("http://www.example.com/0.mp3"),
      (audioBook.spine[0] as ExoSpineElement).itemManifest.uri
    )
    Assertions.assertEquals(
      URI.create("http://www.example.com/1.mp3"),
      (audioBook.spine[1] as ExoSpineElement).itemManifest.uri
    )
    Assertions.assertEquals(
      URI.create("http://www.example.com/2.mp3"),
      (audioBook.spine[2] as ExoSpineElement).itemManifest.uri
    )

    audioBook.replaceManifest(manifest1).get()

    Assertions.assertEquals(
      URI.create("http://www.example.com/0r.mp3"),
      (audioBook.spine[0] as ExoSpineElement).itemManifest.uri
    )
    Assertions.assertEquals(
      URI.create("http://www.example.com/1r.mp3"),
      (audioBook.spine[1] as ExoSpineElement).itemManifest.uri
    )
    Assertions.assertEquals(
      URI.create("http://www.example.com/2r.mp3"),
      (audioBook.spine[2] as ExoSpineElement).itemManifest.uri
    )
  }

  /**
   * Test that manifests with the wrong ID are rejected.
   */

  @Test
  fun testManifestReloadingWrongID() {
    val manifest0 =
      this.parseManifest("ok_minimal_0.json")
    val manifest1 =
      this.parseManifest("ok_minimal_1.json")
    val manifest2 =
      manifest1.copy(
        metadata = manifest1.metadata.copy(
          identifier = "8d4a0b6b-5f4b-4249-b27c-9a02c769d231"
        )
      )

    val request =
      PlayerAudioEngineRequest(
        manifest = manifest0,
        filter = { true },
        downloadProvider = DishonestDownloadProvider(),
        userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
      )

    val engineProvider =
      ExoEngineProvider(threadFactory = ExoEngineThread.Companion::createWithoutPreparation)
    val bookProvider =
      engineProvider.tryRequest(request)!!
    val audioBook =
      (bookProvider.create(this.context()) as PlayerResult.Success).result

    val exception = Assertions.assertThrows(ExecutionException::class.java) {
      audioBook.replaceManifest(manifest2).get()
    }

    Assertions.assertTrue(exception.cause is IllegalArgumentException)
    Assertions.assertTrue(exception.message!!.contains("8d4a0b6b-5f4b-4249-b27c-9a02c769d231"))
  }

  /**
   * Test that manifests with a wrong chapter count are rejected.
   */

  @Test
  fun testManifestReloadingWrongChapters() {
    val manifest0 =
      this.parseManifest("ok_minimal_0.json")
    val manifest1 =
      this.parseManifest("ok_minimal_1.json")
    val manifest2 =
      manifest1.copy(
        readingOrder = manifest1.readingOrder.take(1)
      )

    val request =
      PlayerAudioEngineRequest(
        manifest = manifest0,
        filter = { true },
        downloadProvider = DishonestDownloadProvider(),
        userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
      )

    val engineProvider =
      ExoEngineProvider(threadFactory = ExoEngineThread.Companion::createWithoutPreparation)
    val bookProvider =
      engineProvider.tryRequest(request)!!
    val audioBook =
      (bookProvider.create(this.context()) as PlayerResult.Success).result

    val exception = Assertions.assertThrows(ExecutionException::class.java) {
      audioBook.replaceManifest(manifest2).get()
    }

    Assertions.assertTrue(exception.cause is IllegalArgumentException)
    Assertions.assertTrue(exception.message!!.contains("count 1 does not match existing count 3"))
  }

  /**
   * Test that expiring links are detected correctly.
   */

  @Test
  fun testExpiringLinks() {
    val manifest0 =
      PlayerManifest(
        originalBytes = ByteArray(0),
        readingOrder = listOf(
          PlayerManifestLink.LinkBasic(
            href = URI.create("http://www.example.com"),
            duration = 100.0,
            expires = true
          ),
          PlayerManifestLink.LinkBasic(
            href = URI.create("http://www.example.com"),
            duration = 100.0,
            expires = false
          )
        ),
        metadata = PlayerManifestMetadata(
          title = "Example",
          identifier = "e8b38387-154a-4b7f-8124-8c6e0b6d30bb",
          encrypted = null
        ),
        links = listOf(),
        extensions = listOf(),
        toc = listOf()
      )

    val request =
      PlayerAudioEngineRequest(
        manifest = manifest0,
        filter = { true },
        downloadProvider = FailingDownloadProvider(),
        userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
      )

    val engineProvider =
      ExoEngineProvider(threadFactory = ExoEngineThread.Companion::createWithoutPreparation)
    val bookProvider =
      engineProvider.tryRequest(request)!!
    val result =
      bookProvider.create(this.context())
    val audioBook =
      (result as PlayerResult.Success).result

    audioBook.downloadTasks.first().fetch()
    audioBook.downloadTasks[1].fetch()

    Thread.sleep(1_000L)

    Assertions.assertTrue(
      audioBook.spine[0].downloadStatus is PlayerSpineElementDownloadExpired
    )
    Assertions.assertTrue(
      audioBook.spine[1].downloadStatus is PlayerSpineElementDownloadFailed
    )
  }

  /**
   * Test that manifest update events are delivered.
   */

  @Test
  // @Timeout(20)
  fun testManifestUpdatedEvents() {
    Assumptions.assumeTrue(this.onRealDevice(), "Test is running on a real device")

    val manifest0 =
      PlayerManifest(
        originalBytes = ByteArray(0),
        readingOrder = listOf(
          PlayerManifestLink.LinkBasic(
            href = URI.create("http://www.example.com"),
            duration = 100.0,
            expires = true
          )
        ),
        metadata = PlayerManifestMetadata(
          title = "Example",
          identifier = "e8b38387-154a-4b7f-8124-8c6e0b6d30bb",
          encrypted = null
        ),
        links = listOf(),
        extensions = listOf(),
        toc = listOf()
      )

    val request =
      PlayerAudioEngineRequest(
        manifest = manifest0,
        filter = { true },
        downloadProvider = FailingDownloadProvider(),
        userAgent = PlayerUserAgent("org.librarysimplified.audiobook.tests 1.0.0")
      )

    val engineProvider =
      ExoEngineProvider()
    val bookProvider =
      engineProvider.tryRequest(request)!!
    val result =
      bookProvider.create(this.context())
    val audioBook =
      (result as PlayerResult.Success).result
    val player =
      audioBook.createPlayer()

    val waitLatch = CountDownLatch(1)
    val events = ArrayList<String>()
    this.subscribeToEvents(player, events, waitLatch)

    audioBook.replaceManifest(manifest0)
    Thread.sleep(1_000L)
    player.close()
    waitLatch.await()

    this.showEvents(events)
    Assertions.assertTrue(events.size == 1, "1 event must be logged (${events.size})")
    Assertions.assertEquals("playerManifestUpdated", events.removeAt(0))
  }

  private fun resourceStream(name: String): InputStream {
    return ByteArrayInputStream(this.resource(name))
  }

  private fun resource(name: String): ByteArray {
    val path = "/org/librarysimplified/audiobook/tests/" + name
    return ExoEngineProviderContract::class.java.getResourceAsStream(path)?.readBytes()
      ?: throw AssertionError("Missing resource file: " + path)
  }
}
