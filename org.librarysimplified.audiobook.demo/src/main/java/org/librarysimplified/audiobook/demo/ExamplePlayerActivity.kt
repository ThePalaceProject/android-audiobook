package org.librarysimplified.audiobook.demo

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import org.joda.time.DateTime
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerAudioEngines
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventError
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventManifestUpdated
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
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerSleepTimer
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.api.extensions.PlayerExtensionType
import org.librarysimplified.audiobook.downloads.DownloadProvider
import org.librarysimplified.audiobook.feedbooks.FeedbooksPlayerExtension
import org.librarysimplified.audiobook.feedbooks.FeedbooksPlayerExtensionConfiguration
import org.librarysimplified.audiobook.license_check.api.LicenseCheckParameters
import org.librarysimplified.audiobook.license_check.api.LicenseChecks
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckProviderType
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckStatus
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest_fulfill.api.ManifestFulfillmentStrategies
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicCredentials
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicParameters
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicType
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAManifestFulfillmentStrategyProviderType
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAManifestURI
import org.librarysimplified.audiobook.manifest_fulfill.opa.OPAParameters
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentErrorType
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentEvent
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentStrategyType
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.manifest_parser.extension_spi.ManifestParserExtensionType
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.librarysimplified.audiobook.views.PlayerAccessibilityEvent
import org.librarysimplified.audiobook.views.PlayerFragment
import org.librarysimplified.audiobook.views.PlayerFragmentListenerType
import org.librarysimplified.audiobook.views.PlayerFragmentParameters
import org.librarysimplified.audiobook.views.PlayerPlaybackRateFragment
import org.librarysimplified.audiobook.views.PlayerSleepTimerFragment
import org.librarysimplified.audiobook.views.toc.PlayerTOCFragment
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.vanilla.LSHTTPClients
import org.slf4j.LoggerFactory
import rx.Subscription
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.ServiceLoader
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ExamplePlayerActivity : AppCompatActivity(), PlayerFragmentListenerType {

  private val log =
    LoggerFactory.getLogger(ExamplePlayerActivity::class.java)

  companion object {
    const val FETCH_PARAMETERS_ID =
      "org.nypl.audiobook.demo.android.with_fragments.PlayerActivity.PARAMETERS_ID"
  }

  private val userAgent = PlayerUserAgent("org.librarysimplified.audiobook.demo.main_ui")

  private val httpClient = LSHTTPClients().create(
    context = this,
    configuration = LSHTTPClientConfiguration(
      applicationName = "org.librarysimplified.audiobook.demo.ExamplePlayerActivity",
      applicationVersion = "1.0.0"
    )
  )

  private lateinit var bookmarks: ExampleBookmarkDatabase
  private lateinit var examplePlayerFetchingFragment: ExamplePlayerFetchingFragment
  private lateinit var downloadExecutor: ListeningExecutorService
  private lateinit var uiScheduledExecutor: ScheduledExecutorService
  private lateinit var playerFragment: PlayerFragment
  private lateinit var player: PlayerType
  private var playerInitialized: Boolean = false
  private lateinit var book: PlayerAudioBookType
  private lateinit var bookTitle: String
  private lateinit var bookAuthor: String
  private lateinit var playerEvents: Subscription

  override fun onCreate(state: Bundle?) {
    super.onCreate(null)

    this.setTheme(org.thepalaceproject.theme.core.R.style.PalaceTheme_WithoutActionBar)
    this.setContentView(R.layout.example_player_activity)
    this.supportActionBar?.setTitle(R.string.exAppName)
    this.bookmarks = ExampleBookmarkDatabase(this)

    /*
     * Create executors for download threads, and for scheduling UI events. Each thread is
     * assigned a useful name for correct blame assignment during debugging.
     */

    this.downloadExecutor =
      MoreExecutors.listeningDecorator(
        Executors.newFixedThreadPool(4) { r: Runnable? ->
          val thread = Thread(r)
          thread.name = "org.librarysimplified.audiobook.demo.downloader-${thread.id}"
          thread
        }
      )

    this.uiScheduledExecutor =
      Executors.newSingleThreadScheduledExecutor { r: Runnable? ->
        val thread = Thread(r)
        thread.name = "org.librarysimplified.audiobook.demo.ui-schedule-${thread.id}"
        thread
      }

    /*
     * Create a fragment that shows an indefinite progress bar whilst we do the work
     * of downloading and parsing manifests.
     */

    if (state == null) {
      this.examplePlayerFetchingFragment = ExamplePlayerFetchingFragment.newInstance()

      this.supportFragmentManager
        .beginTransaction()
        .replace(
          R.id.example_player_fragment_holder,
          this.examplePlayerFetchingFragment,
          "PLAYER_FETCHING"
        )
        .commit()
    }

    val args =
      this.intent.extras ?: throw IllegalStateException("No arguments passed to activity")
    val parameters: ExamplePlayerParameters =
      args.getSerializable(FETCH_PARAMETERS_ID) as ExamplePlayerParameters

    /*
     * Start the manifest asynchronously downloading in the background. When the
     * download/parsing operation completes, we will open an audio player using the
     * parsed manifest.
     */

    val manifestFuture =
      this.downloadExecutor.submit(
        Callable {
          return@Callable this.downloadAndParseManifestShowingErrors(parameters)
        }
      )

    manifestFuture.addListener(
      Runnable {
        try {
          this.openPlayerForManifest(
            parameters = parameters,
            manifest = manifestFuture.get(3L, TimeUnit.SECONDS)
          )
        } catch (e: Exception) {
          this.log.error("error downloading manifest: ", e)
        }
      },
      MoreExecutors.directExecutor()
    )
  }

  override fun onDestroy() {
    super.onDestroy()

    try {
      PlayerSleepTimer.cancel()
    } catch (e: Exception) {
      this.log.error("error shutting down sleep timer: ", e)
    }

    try {
      this.downloadExecutor.shutdown()
    } catch (e: Exception) {
      this.log.error("error shutting down download executor: ", e)
    }

    try {
      this.bookmarks.close()
    } catch (e: Exception) {
      this.log.error("error shutting down bookmarks: ", e)
    }

    if (this.playerInitialized) {
      try {
        this.player.close()
      } catch (e: Exception) {
        this.log.error("error closing player: ", e)
      }

      try {
        this.playerEvents.unsubscribe()
      } catch (e: Exception) {
        this.log.error("error closing subscription: ", e)
      }

      try {
        this.book.close()
      } catch (e: Exception) {
        this.log.error("error closing book: ", e)
      }
    }
  }

  /**
   * Attempt to parse a manifest file.
   */

  private fun parseManifest(
    source: URI,
    data: ByteArray
  ): ParseResult<PlayerManifest> {
    this.log.debug("parseManifest")

    val extensions =
      ServiceLoader.load(ManifestParserExtensionType::class.java)
        .toList()

    return ManifestParsers.parse(
      uri = source,
      streams = data,
      extensions = extensions
    )
  }

  /**
   * Attempt to synchronously download a manifest file. If the download fails, return the
   * error details.
   */

  private fun downloadManifest(
    parameters: ExamplePlayerParameters
  ): PlayerResult<ManifestFulfilled, ManifestFulfillmentErrorType> {
    this.log.debug("downloadManifest")

    val credentials =
      parameters.credentials
    val strategy: ManifestFulfillmentStrategyType =
      this.downloadStrategyForCredentials(credentials, parameters)
    val fulfillSubscription =
      strategy.events.subscribe(this::onManifestFulfillmentEvent)

    try {
      return strategy.execute()
    } finally {
      fulfillSubscription.unsubscribe()
    }
  }

  private fun downloadStrategyForCredentials(
    credentials: ExamplePlayerCredentials,
    parameters: ExamplePlayerParameters
  ): ManifestFulfillmentStrategyType {
    return when (credentials) {
      is ExamplePlayerCredentials.None -> {
        val strategies =
          ManifestFulfillmentStrategies.findStrategy(ManifestFulfillmentBasicType::class.java)
            ?: throw UnsupportedOperationException()

        strategies.create(
          ManifestFulfillmentBasicParameters(
            uri = URI.create(parameters.fetchURI),
            credentials = null,
            httpClient = this.httpClient,
            userAgent = this.userAgent
          )
        )
      }

      is ExamplePlayerCredentials.Basic -> {
        val strategies =
          ManifestFulfillmentStrategies.findStrategy(ManifestFulfillmentBasicType::class.java)
            ?: throw UnsupportedOperationException()

        val credentials =
          ManifestFulfillmentBasicCredentials(
            userName = credentials.userName,
            password = credentials.password
          )

        strategies.create(
          ManifestFulfillmentBasicParameters(
            uri = URI.create(parameters.fetchURI),
            credentials = credentials,
            httpClient = this.httpClient,
            userAgent = this.userAgent
          )
        )
      }

      is ExamplePlayerCredentials.Feedbooks -> {
        val strategies =
          ManifestFulfillmentStrategies.findStrategy(ManifestFulfillmentBasicType::class.java)
            ?: throw UnsupportedOperationException()

        val credentials =
          ManifestFulfillmentBasicCredentials(
            userName = credentials.userName,
            password = credentials.password
          )

        strategies.create(
          ManifestFulfillmentBasicParameters(
            uri = URI.create(parameters.fetchURI),
            credentials = credentials,
            httpClient = this.httpClient,
            userAgent = this.userAgent
          )
        )
      }

      is ExamplePlayerCredentials.Overdrive -> {
        val strategies =
          ManifestFulfillmentStrategies.findStrategy(
            OPAManifestFulfillmentStrategyProviderType::class.java
          ) ?: throw UnsupportedOperationException()

        strategies.create(
          OPAParameters(
            userName = credentials.userName,
            password = credentials.password,
            clientKey = credentials.clientKey,
            clientPass = credentials.clientPass,
            targetURI = OPAManifestURI.Indirect(URI.create(parameters.fetchURI)),
            userAgent = this.userAgent
          )
        )
      }
    }
  }

  private fun onManifestFulfillmentEvent(event: ManifestFulfillmentEvent) {
    ExampleUIThread.runOnUIThread(
      Runnable {
        this.examplePlayerFetchingFragment.setMessageText(event.message)
      }
    )
  }

  /**
   * Attempt to perform any required license checks on the manifest.
   */

  private fun checkManifest(
    manifest: PlayerManifest
  ): Boolean {
    val singleChecks =
      ServiceLoader.load(SingleLicenseCheckProviderType::class.java)
        .toList()
    val check =
      LicenseChecks.createLicenseCheck(
        LicenseCheckParameters(
          manifest = manifest,
          userAgent = this.userAgent,
          checks = singleChecks,
          cacheDirectory = this.cacheDir
        )
      )

    val checkSubscription =
      check.events.subscribe { event ->
        this.onLicenseCheckEvent(event)
      }

    try {
      val checkResult = check.execute()
      return checkResult.checkSucceeded()
    } finally {
      checkSubscription.unsubscribe()
    }
  }

  private fun onLicenseCheckEvent(event: SingleLicenseCheckStatus) {
    ExampleUIThread.runOnUIThread(
      Runnable {
        this.examplePlayerFetchingFragment.setMessageText(event.message)
      }
    )
  }

  /**
   * Attempt to download and parse the audio book manifest. This composes [downloadManifest]
   * [parseManifest], and [checkManifest], with the main difference that errors are logged to the
   * UI thread on failure and the activity is closed.
   */

  private fun downloadAndParseManifestShowingErrors(
    parameters: ExamplePlayerParameters
  ): PlayerManifest {
    this.log.debug("downloadAndParseManifestShowingErrors")

    val downloadResult = this.downloadManifest(parameters)
    if (downloadResult is PlayerResult.Failure) {
      val exception = IOException()
      ExampleErrorDialogUtilities.showErrorWithRunnable(
        this@ExamplePlayerActivity,
        this.log,
        "Failed to download manifest: ${downloadResult.failure.message}",
        exception,
        Runnable {
          this.finish()
        }
      )
      throw exception
    }

    val (_, _, downloadBytes) = (downloadResult as PlayerResult.Success).result
    cacheManifest(downloadBytes)

    val parseResult =
      this.parseManifest(URI.create(parameters.fetchURI), downloadBytes)
    if (parseResult is ParseResult.Failure) {
      val exception = IOException()
      ExampleErrorDialogUtilities.showErrorWithRunnable(
        this@ExamplePlayerActivity,
        this.log,
        "Failed to parse manifest: ${parseResult.errors[0].message}",
        exception,
        Runnable {
          this.finish()
        }
      )
      throw exception
    }

    val (_, parsedManifest) = parseResult as ParseResult.Success
    if (!this.checkManifest(parsedManifest)) {
      val exception = IOException()
      ExampleErrorDialogUtilities.showErrorWithRunnable(
        this@ExamplePlayerActivity,
        this.log,
        "One or more license checks failed for the audio book manifest.",
        exception,
        Runnable {
          this.finish()
        }
      )
      throw exception
    }

    return parsedManifest
  }

  private fun cacheManifest(
    downloadBytes: ByteArray
  ) {
    val fileName = UUID.randomUUID().toString() + ".json"
    val filePath = File(this.cacheDir, fileName)
    this.log.debug("saved manifest at {}", filePath)
    filePath.writeBytes(downloadBytes)
  }

  private fun openPlayerForManifest(
    parameters: ExamplePlayerParameters,
    manifest: PlayerManifest
  ) {
    this.log.debug("openPlayerForManifest")

    /*
     * Ask the API for the best audio engine available that can handle the given manifest.
     */

    val engine =
      PlayerAudioEngines.findBestFor(
        PlayerAudioEngineRequest(
          manifest = manifest,
          filter = { true },
          downloadProvider = DownloadProvider.create(this.downloadExecutor),
          userAgent = this.userAgent
        )
      )

    if (engine == null) {
      ExampleErrorDialogUtilities.showErrorWithRunnable(
        this@ExamplePlayerActivity,
        this.log,
        "No audio engine available to handle the given book",
        null,
        Runnable {
          this.finish()
        }
      )
      return
    }

    this.log.debug(
      "selected audio engine: {} {}",
      engine.engineProvider.name(),
      engine.engineProvider.version()
    )

    val extensions = this.configurePlayerExtensions(parameters)

    /*
     * Create the audio book.
     */

    val bookResult =
      engine.bookProvider.create(
        context = this.application,
        extensions = extensions
      )

    if (bookResult is PlayerResult.Failure) {
      ExampleErrorDialogUtilities.showErrorWithRunnable(
        this@ExamplePlayerActivity,
        this.log,
        "Error parsing manifest",
        bookResult.failure,
        Runnable {
          this.finish()
        }
      )
      return
    }

    this.book = (bookResult as PlayerResult.Success).result
    this.bookTitle = manifest.metadata.title
    this.bookAuthor = "Unknown Author"
    this.player = this.book.createPlayer()
    this.playerInitialized = true

    val lastPlayed = this.bookmarks.bookmarkFindLastReadLocation(book.id.value)
    this.player.movePlayheadToLocation(lastPlayed, playAutomatically = false)
    this.playerEvents = this.player.events.subscribe(this::onPlayerEvent)

    this.startAllPartsDownloading()

    /*
     * Create and load the main player fragment into the holder view declared in the activity.
     */

    ExampleUIThread.runOnUIThread(
      Runnable {
        this.playerFragment = PlayerFragment.newInstance(PlayerFragmentParameters())

        this.supportFragmentManager
          .beginTransaction()
          .replace(R.id.example_player_fragment_holder, this.playerFragment, "PLAYER")
          .commit()
      }
    )
  }

  private fun startAllPartsDownloading() {
    this.book.wholeBookDownloadTask.fetch()
  }

  /**
   * Configure any extensions that we want to use.
   */

  private fun configurePlayerExtensions(
    parameters: ExamplePlayerParameters
  ): List<PlayerExtensionType> {
    val extensions =
      ServiceLoader.load(PlayerExtensionType::class.java)
        .toList()

    this.log.debug("{} player extensions available", extensions.size)
    extensions.forEachIndexed { index, extension ->
      this.log.debug("[{}] extension: {}", index, extension.name)
    }

    val feedbooksExtension: FeedbooksPlayerExtension? =
      extensions.filterIsInstance(FeedbooksPlayerExtension::class.java)
        .firstOrNull()

    if (feedbooksExtension != null) {
      this.log.debug("feedbooks extension is present")
      when (val credentials = parameters.credentials) {
        is ExamplePlayerCredentials.Feedbooks -> {
          this.log.debug("configuring feedbooks extension")
          feedbooksExtension.configuration =
            FeedbooksPlayerExtensionConfiguration(
              bearerTokenSecret = credentials.bearerTokenSecret,
              issuerURL = credentials.issuerURL
            )
        }

        else -> {
          this.log.debug("no feedbooks extension configuration is necessary")
        }
      }
    }

    return extensions
  }

  override fun onPlayerWantsPlayer(): PlayerType {
    this.log.debug("onPlayerWantsPlayer")
    return this.player
  }

  override fun onPlayerWantsCoverImage(view: ImageView) {
    this.log.debug("onPlayerWantsCoverImage: {}", view)
    view.setImageResource(R.drawable.example_cover)
    view.setOnLongClickListener {
      val toast = Toast.makeText(this, "Deleted local book data", Toast.LENGTH_SHORT)
      toast.show()
      this.book.wholeBookDownloadTask.delete()
      true
    }
  }

  override fun onPlayerTOCWantsBookmarks(): List<PlayerBookmark> {
    return listOf(
      PlayerBookmark(
        DateTime.now(),
        PlayerPosition("Example 1", 1, 1, 0L, 0L),
        100L,
        null
      ),
      PlayerBookmark(
        DateTime.now(),
        PlayerPosition("Example 2", 2, 1, 0L, 0L),
        100L,
        null
      ),
      PlayerBookmark(
        DateTime.now(),
        PlayerPosition("Example 3", 3, 1, 0L, 0L),
        100L,
        null
      )
    )
  }

  override fun onPlayerShouldDeleteBookmark(
    playerBookmark: PlayerBookmark,
    onDeleteOperationCompleted: (Boolean) -> Unit
  ) {
    // do nothing
  }

  override fun onPlayerNotificationWantsBookCover(onBookCoverLoaded: (Bitmap) -> Unit) {
    onBookCoverLoaded(BitmapFactory.decodeResource(resources, R.drawable.example_cover))
  }

  override fun onPlayerNotificationWantsSmallIcon(): Int {
    return org.librarysimplified.audiobook.views.R.drawable.baseline_work_24
  }

  override fun onPlayerNotificationWantsIntent(): Intent {
    return Intent(this, ExampleConfigurationActivity::class.java).apply {
      addCategory(Intent.CATEGORY_LAUNCHER)
      setAction(Intent.ACTION_MAIN)
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
  }

  override fun onPlayerTOCWantsBook(): PlayerAudioBookType {
    this.log.debug("onPlayerTOCWantsBook")
    return this.book
  }

  override fun onPlayerTOCShouldOpen() {
    this.log.debug("onPlayerTOCShouldOpen")

    /*
     * The player fragment wants us to open the table of contents. Load and display it, and
     * also set the action bar title.
     */

    ExampleUIThread.runOnUIThread(
      Runnable {
        this.supportActionBar?.setTitle(R.string.exTableOfContents)

        val fragment =
          PlayerTOCFragment.newInstance()

        this.supportFragmentManager
          .beginTransaction()
          .hide(this.playerFragment)
          .add(R.id.example_player_fragment_holder, fragment, "PLAYER_TOC")
          .addToBackStack(null)
          .commit()
      }
    )
  }

  override fun onPlayerSleepTimerShouldOpen() {
    this.log.debug("onPlayerSleepTimerShouldOpen")

    /*
     * The player fragment wants us to open the sleep timer.
     */

    ExampleUIThread.runOnUIThread(
      Runnable {
        val fragment =
          PlayerSleepTimerFragment.newInstance()
        fragment.show(this.supportFragmentManager, "PLAYER_SLEEP_TIMER")
      }
    )
  }

  override fun onPlayerPlaybackRateShouldOpen() {
    this.log.debug("onPlayerPlaybackRateShouldOpen")

    /*
     * The player fragment wants us to open the playback rate selection dialog.
     */

    ExampleUIThread.runOnUIThread(
      Runnable {
        val fragment =
          PlayerPlaybackRateFragment.newInstance(PlayerFragmentParameters())
        fragment.show(this.supportFragmentManager, "PLAYER_RATE")
      }
    )
  }

  override fun onPlayerShouldBeClosed() {
    onBackPressed()
  }

  override fun onPlayerTOCWantsClose() {
    this.log.debug("onPlayerTOCWantsClose")
    this.supportFragmentManager.popBackStack()
  }

  override fun onPlayerWantsTitle(): String {
    this.log.debug("onPlayerWantsTitle")
    return this.bookTitle
  }

  override fun onPlayerWantsAuthor(): String {
    this.log.debug("onPlayerWantsAuthor")
    return this.bookAuthor
  }

  override fun onPlayerShouldAddBookmark(playerBookmark: PlayerBookmark?) {
    this.log.debug("Bookmark added: $playerBookmark")
    Toast.makeText(this, "Bookmark Added", Toast.LENGTH_SHORT).show()
  }

  override fun onPlayerWantsScheduledExecutor(): ScheduledExecutorService {
    return this.uiScheduledExecutor
  }

  override fun onPlayerAccessibilityEvent(event: PlayerAccessibilityEvent) {
    ExampleUIThread.runOnUIThread(
      Runnable {
        val toast = Toast.makeText(this.applicationContext, event.message, Toast.LENGTH_LONG)
        toast.show()
      }
    )
  }

  private fun onPlayerEvent(event: PlayerEvent) {
    return when (event) {
      is PlayerEventCreateBookmark -> {
        this.bookmarks.bookmarkSave(
          this.book.id.value,
          PlayerPosition(
            title = event.spineElement.title,
            part = event.spineElement.position.part,
            chapter = event.spineElement.position.chapter,
            startOffset = event.spineElement.position.startOffset,
            currentOffset = event.spineElement.position.startOffset + event.offsetMilliseconds
          )
        )
      }

      is PlayerEventError,
      PlayerEventManifestUpdated,
      is PlayerEventPlaybackRateChanged,
      is PlayerEventChapterCompleted,
      is PlayerEventChapterWaiting,
      is PlayerEventPlaybackBuffering,
      is PlayerEventPlaybackPaused,
      is PlayerEventPlaybackProgressUpdate,
      is PlayerEventPlaybackStarted,
      is PlayerEventPlaybackStopped,
      is PlayerEventPlaybackWaitingForAction ->
        Unit
    }
  }
}
