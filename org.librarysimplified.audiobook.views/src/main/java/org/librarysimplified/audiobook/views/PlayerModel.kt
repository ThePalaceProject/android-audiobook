package org.librarysimplified.audiobook.views

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import androidx.annotation.UiThread
import androidx.core.content.getSystemService
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerAudioEngines
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerPlaybackIntention
import org.librarysimplified.audiobook.api.PlayerPlaybackRate
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerSleepTimer
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration
import org.librarysimplified.audiobook.api.PlayerSleepTimerType
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.api.extensions.PlayerExtensionType
import org.librarysimplified.audiobook.downloads.DownloadProvider
import org.librarysimplified.audiobook.license_check.api.LicenseCheckParameters
import org.librarysimplified.audiobook.license_check.api.LicenseChecks
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckProviderType
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckStatus
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentErrorType
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentEvent
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentStrategyType
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.manifest_parser.extension_spi.ManifestParserExtensionType
import org.librarysimplified.audiobook.parser.api.ParseError
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerBookOpenFailed
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerManifestDownloadFailed
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerManifestInProgress
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerManifestLicenseChecksFailed
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerManifestOK
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerManifestParseFailed
import org.librarysimplified.http.api.LSHTTPAuthorizationType
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.ServiceLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

object PlayerModel {

  @Volatile
  var playerExtensions: List<PlayerExtensionType> =
    ServiceLoader.load(PlayerExtensionType::class.java)
      .toList()

  @Volatile
  private var intentForPlayerServiceField: Intent? = null

  val notificationIntentForPlayerService: Intent?
    get() = this.intentForPlayerServiceField

  @Volatile
  var bookTitle: String = ""

  @Volatile
  var bookAuthor: String = ""

  @Volatile
  private var coverImageField: Bitmap? = null

  /**
   * The cover image for the audio book.
   */

  val coverImage: Bitmap?
    get() = this.coverImageField

  private var audioManagerService: AudioManager? = null
  val playbackRate: PlayerPlaybackRate
    get() = this.playerAndBookField?.player?.playbackRate ?: PlayerPlaybackRate.NORMAL_TIME

  private val logger =
    LoggerFactory.getLogger(PlayerModel::class.java)

  private val downloadExecutor =
    Executors.newFixedThreadPool(1) { r: Runnable ->
      val thread = Thread(r)
      thread.name = "org.librarysimplified.audiobook.views.PlayerModel.downloader-${thread.id}"
      thread.priority = Thread.MIN_PRIORITY
      thread
    }

  private val taskExecutor =
    Executors.newFixedThreadPool(1) { r: Runnable ->
      val thread = Thread(r)
      thread.name = "org.librarysimplified.audiobook.views.PlayerModel.task-${thread.id}"
      thread.priority = Thread.MIN_PRIORITY
      thread
    }

  private var currentFuture: CompletableFuture<Unit>? = null

  private fun executeTaskCancellingExisting(
    task: () -> Unit
  ): CompletableFuture<Unit> {
    val newFuture = CompletableFuture<Unit>()
    this.currentFuture?.cancel(true)
    this.currentFuture = newFuture

    this.taskExecutor.execute {
      try {
        newFuture.complete(task.invoke())
      } catch (e: Throwable) {
        this.logger.error("Task exception: ", e)
        newFuture.completeExceptionally(e)
      }
    }
    return newFuture
  }

  private class OperationFailedException : Exception()

  @Volatile
  private var playerAndBookField: PlayerBookAndPlayer? = null

  @Volatile
  private var stateField: PlayerModelState =
    PlayerModelState.PlayerClosed

  val state: PlayerModelState
    get() = this.stateField

  private val stateSubject =
    BehaviorSubject.create<PlayerModelState>()
      .toSerialized()

  private val manifestDownloadEventSubject =
    PublishSubject.create<ManifestFulfillmentEvent>()
      .toSerialized()

  private val licenseCheckEventSubject =
    PublishSubject.create<SingleLicenseCheckStatus>()
      .toSerialized()

  /**
   * A source of manifest fulfillment events that are always observed on the UI thread.
   */

  val manifestDownloadEvents: Observable<ManifestFulfillmentEvent> =
    this.manifestDownloadEventSubject.observeOn(AndroidSchedulers.mainThread())

  @Volatile
  private var manifestDownloadLogField: List<ManifestFulfillmentEvent> =
    listOf()

  /*
   * The events that have been published from the most recent manifest operation.
   */

  val manifestDownloadLog: List<ManifestFulfillmentEvent>
    get() = this.manifestDownloadLogField

  /**
   * A source of license check events that are always observed on the UI thread.
   */

  val singleLicenseCheckEvents: Observable<SingleLicenseCheckStatus> =
    this.licenseCheckEventSubject.observeOn(AndroidSchedulers.mainThread())

  @Volatile
  private var singleLicenseCheckLogField: List<SingleLicenseCheckStatus> =
    listOf()

  /*
   * The events that have been published from the most recent license check operation.
   */

  val singleLicenseCheckLog: List<SingleLicenseCheckStatus>
    get() = this.singleLicenseCheckLogField

  @Volatile
  private var manifestParseErrorLogField: List<ParseError> =
    listOf()

  /*
   * The error events that have been published from the most manifest parsing operation.
   */

  val manifestParseErrorLog: List<ParseError>
    get() = this.manifestParseErrorLogField

  /**
   * A source of model state events.
   */

  val stateEvents: Observable<PlayerModelState> =
    this.stateSubject.observeOn(AndroidSchedulers.mainThread())

  private val playerEventSubject =
    BehaviorSubject.create<PlayerEvent>()
      .toSerialized()

  /**
   * A source of player events.
   */

  val playerEvents: Observable<PlayerEvent> =
    this.playerEventSubject.observeOn(AndroidSchedulers.mainThread())

  private val viewCommandSource =
    PublishSubject.create<PlayerViewCommand>()
      .toSerialized()

  /**
   * A source of player view commands.
   */

  val viewCommands: Observable<PlayerViewCommand> =
    this.viewCommandSource.observeOn(AndroidSchedulers.mainThread())

  /*
   * A source of download status events.
   */

  private val downloadEventSubject =
    BehaviorSubject.create<PlayerReadingOrderItemDownloadStatus>()
      .toSerialized()

  val downloadEvents: Observable<PlayerReadingOrderItemDownloadStatus> =
    this.downloadEventSubject.observeOn(AndroidSchedulers.mainThread())

  init {
    this.stateSubject.onNext(this.state)
  }

  @UiThread
  fun submitViewCommand(command: PlayerViewCommand) {
    PlayerUIThread.checkIsUIThread()
    this.viewCommandSource.onNext(command)
  }

  private fun downloadManifest(
    strategy: ManifestFulfillmentStrategyType
  ): PlayerResult<ManifestFulfilled, ManifestFulfillmentErrorType> {
    this.logger.debug("downloadManifest")

    val fulfillSubscription =
      strategy.events.subscribe { event ->
        this.manifestDownloadLogField = this.manifestDownloadLogField.plus(event)
        this.manifestDownloadEventSubject.onNext(event)
      }

    try {
      return strategy.execute()
    } finally {
      fulfillSubscription.dispose()
    }
  }

  /**
   * Attempt to perform any required license checks on the manifest.
   */

  private fun checkManifest(
    manifest: PlayerManifest,
    userAgent: PlayerUserAgent,
    licenseChecks: List<SingleLicenseCheckProviderType>,
    cacheDir: File
  ): Boolean {
    this.logger.debug("checkManifest")

    val check =
      LicenseChecks.createLicenseCheck(
        LicenseCheckParameters(
          manifest = manifest,
          userAgent = userAgent,
          checks = licenseChecks,
          cacheDirectory = cacheDir
        )
      )

    val checkSubscription =
      check.events.subscribe { event ->
        this.singleLicenseCheckLogField = this.singleLicenseCheckLogField.plus(event)
        this.licenseCheckEventSubject.onNext(event)
      }

    try {
      val checkResult = check.execute()
      return checkResult.checkSucceeded()
    } finally {
      checkSubscription.dispose()
    }
  }

  /**
   * Attempt to parse a manifest file.
   */

  private fun parseManifest(
    source: URI,
    extensions: List<ManifestParserExtensionType>,
    data: ByteArray
  ): ParseResult<PlayerManifest> {
    this.logger.debug("parseManifest")

    return ManifestParsers.parse(
      uri = source,
      streams = data,
      extensions = extensions
    )
  }

  /**
   * Attempt to download and parse the audio book manifest.
   */

  fun downloadParseAndCheckManifest(
    sourceURI: URI,
    userAgent: PlayerUserAgent,
    cacheDir: File,
    licenseChecks: List<SingleLicenseCheckProviderType>,
    strategy: ManifestFulfillmentStrategyType,
    parserExtensions: List<ManifestParserExtensionType>
  ): CompletableFuture<Unit> {
    this.logger.debug("downloadAndParseManifestShowingErrors")

    return this.executeTaskCancellingExisting {
      this.opDownloadAndParseManifest(
        strategy,
        sourceURI,
        parserExtensions,
        userAgent,
        licenseChecks,
        cacheDir
      )
    }
  }

  private fun opDownloadAndParseManifest(
    strategy: ManifestFulfillmentStrategyType,
    sourceURI: URI,
    parserExtensions: List<ManifestParserExtensionType>,
    userAgent: PlayerUserAgent,
    licenseChecks: List<SingleLicenseCheckProviderType>,
    cacheDir: File
  ) {
    this.manifestDownloadLogField = listOf()
    this.singleLicenseCheckLogField = listOf()
    this.manifestParseErrorLogField = listOf()

    this.setNewState(PlayerManifestInProgress)

    val downloadResult = this.downloadManifest(strategy)
    if (downloadResult is PlayerResult.Failure) {
      this.setNewState(PlayerManifestDownloadFailed(downloadResult.failure))
      throw OperationFailedException()
    }

    val result = (downloadResult as PlayerResult.Success).result
    this.configurePlayerExtensions(result.authorization)

    val parseResult =
      this.parseManifest(
        source = sourceURI,
        extensions = parserExtensions,
        data = result.data
      )

    if (parseResult is ParseResult.Failure) {
      this.manifestParseErrorLogField = parseResult.errors.toList()
      this.setNewState(PlayerManifestParseFailed(parseResult.errors))
      throw OperationFailedException()
    }

    val (_, parsedManifest) = parseResult as ParseResult.Success
    if (!this.checkManifest(
        manifest = parsedManifest,
        userAgent = userAgent,
        licenseChecks = licenseChecks,
        cacheDir = cacheDir
      )
    ) {
      this.setNewState(PlayerManifestLicenseChecksFailed)
      throw OperationFailedException()
    }

    this.setNewState(PlayerManifestOK(parsedManifest))
  }

  private fun configurePlayerExtensions(
    authorization: LSHTTPAuthorizationType?
  ) {
    this.logger.debug("Providing authorization to player extensions…")

    this.playerExtensions.forEachIndexed { index, extension ->
      this.logger.debug("configurePlayerExtensions: [{}] extension {}", index, extension.name)
      extension.setAuthorization(authorization)
    }
  }

  fun openPlayerForManifest(
    context: Application,
    userAgent: PlayerUserAgent,
    manifest: PlayerManifest
  ): CompletableFuture<Unit> {
    this.logger.debug("openPlayerForManifest")

    if (this.playerExtensions.isEmpty()) {
      this.logger.debug("openPlayerForManifest: No player extensions were provided.")
    } else {
      this.playerExtensions.forEachIndexed { index, extension ->
        this.logger.debug("openPlayerForManifest: [{}] extension {}", index, extension.name)
      }
    }

    this.audioManagerService =
      context.getSystemService<AudioManager>()

    return this.executeTaskCancellingExisting {
      this.opOpenPlayerForManifest(
        manifest = manifest,
        userAgent = userAgent,
        context = context,
        extensions = this.playerExtensions
      )
    }
  }

  private fun opOpenPlayerForManifest(
    manifest: PlayerManifest,
    userAgent: PlayerUserAgent,
    context: Application,
    extensions: List<PlayerExtensionType>
  ) {
    this.logger.debug("opOpenPlayerForManifest")

    /*
     * Ask the API for the best audio engine available that can handle the given manifest.
     */

    val engine =
      PlayerAudioEngines.findBestFor(
        PlayerAudioEngineRequest(
          manifest = manifest,
          filter = { true },
          downloadProvider = DownloadProvider.create(this.downloadExecutor),
          userAgent = userAgent
        )
      )

    if (engine == null) {
      this.setNewState(PlayerBookOpenFailed("No suitable audio engine for manifest."))
      throw OperationFailedException()
    }

    this.logger.debug(
      "Selected audio engine: {} {}",
      engine.engineProvider.name(),
      engine.engineProvider.version()
    )

    /*
     * Create the audio book.
     */

    val bookResult =
      engine.bookProvider.create(
        context = context,
        extensions = extensions
      )

    if (bookResult is PlayerResult.Failure) {
      this.setNewState(PlayerBookOpenFailed("Failed to open audio book."))
      throw OperationFailedException()
    }

    val newBook =
      (bookResult as PlayerResult.Success).result
    val newPlayer =
      newBook.createPlayer()
    val newPair =
      PlayerBookAndPlayer(newBook, newPlayer)

    this.playerAndBookField?.close()
    this.playerAndBookField = newPair

    newPlayer.events.subscribe(
      { event -> this.playerEventSubject.onNext(event) },
      { exception -> this.logger.error("Player exception: ", exception) }
    )

    newBook.readingOrderElementDownloadStatus.subscribe(
      { event -> this.downloadEventSubject.onNext(event) },
      { exception -> this.logger.error("Download exception: ", exception) }
    )

    this.setNewState(PlayerModelState.PlayerOpen(newPair))
  }

  fun closeBookOrDismissError(): CompletableFuture<Unit> {
    return this.executeTaskCancellingExisting {
      this.opCloseBookOrDismissError()
    }
  }

  private fun opCloseBookOrDismissError() {
    this.currentFuture?.cancel(true)

    when (val current = this.stateField) {
      is PlayerBookOpenFailed ->
        this.setNewState(PlayerModelState.PlayerClosed)

      PlayerModelState.PlayerClosed ->
        Unit

      is PlayerManifestDownloadFailed ->
        this.setNewState(PlayerModelState.PlayerClosed)

      PlayerManifestLicenseChecksFailed ->
        this.setNewState(PlayerModelState.PlayerClosed)

      is PlayerManifestOK ->
        this.setNewState(PlayerModelState.PlayerClosed)

      is PlayerManifestParseFailed ->
        this.setNewState(PlayerModelState.PlayerClosed)

      is PlayerModelState.PlayerOpen -> {
        current.player.close()
        PlayerBookmarkModel.clearBookmarks()
        this.setNewState(PlayerModelState.PlayerClosed)
      }

      PlayerManifestInProgress -> {
        this.setNewState(PlayerModelState.PlayerClosed)
      }
    }
  }

  private fun setNewState(newState: PlayerModelState) {
    this.stateField = newState
    this.stateSubject.onNext(newState)
  }

  fun seekTo(milliseconds: Long) {
    this.playerAndBookField?.player?.seekTo(milliseconds)
  }

  fun play() {
    this.playerAndBookField?.player?.play()

    when (PlayerSleepTimer.status) {
      is PlayerSleepTimerType.Status.Paused -> PlayerSleepTimer.unpause()
      is PlayerSleepTimerType.Status.Running -> {
        // Nothing to do
      }

      is PlayerSleepTimerType.Status.Stopped -> {
        when (PlayerSleepTimer.configuration) {
          PlayerSleepTimerConfiguration.EndOfChapter -> PlayerSleepTimer.start()
          PlayerSleepTimerConfiguration.Off -> {
            // Nothing to do
          }

          is PlayerSleepTimerConfiguration.WithDuration -> PlayerSleepTimer.start()
        }
      }
    }
  }

  fun pause() {
    this.playerAndBookField?.player?.pause()
    PlayerSleepTimer.pause()
  }

  fun playOrPauseAsAppropriate() {
    val playerCurrent = this.playerAndBookField?.player
    when (playerCurrent?.playbackIntention) {
      PlayerPlaybackIntention.SHOULD_BE_PLAYING -> this.pause()
      PlayerPlaybackIntention.SHOULD_BE_STOPPED -> this.play()
      null -> {
        // Nothing to do.
      }
    }
  }

  fun skipToNext() {
    this.playerAndBookField?.player?.skipToNextChapter(0L)
  }

  fun skipToPrevious() {
    this.playerAndBookField?.player?.skipToPreviousChapter(0L)
  }

  fun skipForward() {
    this.playerAndBookField?.player?.skipPlayhead(30_000L)
  }

  fun skipBack() {
    this.playerAndBookField?.player?.skipPlayhead(-30_000L)
  }

  fun book(): PlayerAudioBookType {
    return this.playerAndBookField?.audioBook
      ?: throw IllegalStateException("Player and book are not open!")
  }

  fun movePlayheadTo(playerPosition: PlayerPosition) {
    this.playerAndBookField?.player?.movePlayheadToLocation(playerPosition)
  }

  fun manifest(): PlayerManifest {
    return this.playerAndBookField?.audioBook?.manifest
      ?: throw IllegalStateException("Player and book are not open!")
  }

  fun bookmarkCreate() {
    this.playerAndBookField?.player?.bookmark()
  }

  fun bookmarkDelete(bookmark: PlayerBookmark) {
    this.playerAndBookField?.player?.bookmarkDelete(bookmark)
  }

  fun setPlaybackRate(item: PlayerPlaybackRate) {
    this.playerAndBookField?.player?.playbackRate = item
  }

  val isPlaying: Boolean
    get() = this.playerAndBookField?.player?.playbackIntention == PlayerPlaybackIntention.SHOULD_BE_PLAYING

  fun setCoverImage(image: Bitmap?) {
    this.coverImageField = image
    this.submitViewCommand(PlayerViewCommand.PlayerViewCoverImageChanged)
  }

  /**
   * Start a background service for the player. This is responsible for handling media buttons
   * (such as the player views embedded in lock screens in some devices), and for handling
   * notifications.
   *
   * The included [intentForPlayerService] parameter is an intent that will be published whenever
   * the user clicks published notifications. Typically, this should be an intent that opens the
   * activity that is hosting the player.
   *
   * @param context The application
   * @param intentForPlayerService The intent that will be published when the user clicks notifications
   */

  fun startService(
    context: Application,
    intentForPlayerService: Intent
  ) {
    this.intentForPlayerServiceField = intentForPlayerService
    val intent = Intent(context, PlayerService::class.java)
    context.startService(intent)
  }
}
