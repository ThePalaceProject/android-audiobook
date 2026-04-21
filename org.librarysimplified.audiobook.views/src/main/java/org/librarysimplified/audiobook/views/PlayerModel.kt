package org.librarysimplified.audiobook.views

import android.app.Application
import android.graphics.Bitmap
import android.media.AudioManager
import androidx.annotation.GuardedBy
import androidx.annotation.UiThread
import androidx.core.content.getSystemService
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerAudioEngines
import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerDelegating
import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerType
import org.librarysimplified.audiobook.api.PlayerBookCredentialsType
import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerBookSource
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerBookmarkKind
import org.librarysimplified.audiobook.api.PlayerDownloadProgress
import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus
import org.librarysimplified.audiobook.api.PlayerDownloadTaskType
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventPlaybackRateChanged
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventChapterCompleted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPaused
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStarted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStopped
import org.librarysimplified.audiobook.api.PlayerPauseReason
import org.librarysimplified.audiobook.api.PlayerPlaybackRate
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerSleepTimer
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent
import org.librarysimplified.audiobook.api.PlayerSleepTimerType
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.librarysimplified.audiobook.api.extensions.PlayerAuthorizationHandlerExtensionType
import org.librarysimplified.audiobook.downloads.DownloadProvider
import org.librarysimplified.audiobook.lcp.downloads.LCPDownloads
import org.librarysimplified.audiobook.lcp.downloads.LCPLicenseAndBytes
import org.librarysimplified.audiobook.license_check.api.LicenseCheckParameters
import org.librarysimplified.audiobook.license_check.api.LicenseCheckResult
import org.librarysimplified.audiobook.license_check.api.LicenseChecks
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckProviderType
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckStatus
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOC
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsAbsolute
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsReadingOrderItem
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.manifest_fulfill.basic.ManifestFulfillmentBasicParameters
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfilled
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentError
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentEvent
import org.librarysimplified.audiobook.manifest_fulfill.spi.ManifestFulfillmentStrategyType
import org.librarysimplified.audiobook.manifest_parser.api.ManifestParsers
import org.librarysimplified.audiobook.manifest_parser.api.ManifestUnparsed
import org.librarysimplified.audiobook.manifest_parser.extension_spi.ManifestParserExtensionType
import org.librarysimplified.audiobook.parser.api.ParseError
import org.librarysimplified.audiobook.parser.api.ParseResult
import org.librarysimplified.audiobook.persistence.ADatabaseType
import org.librarysimplified.audiobook.persistence.ADatabases
import org.librarysimplified.audiobook.time_tracking.PlayerTimeTracker
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerBookOpenFailed
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerManifestDownloadFailed
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerManifestInProgress
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerManifestLicenseChecksFailed
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerManifestOK
import org.librarysimplified.audiobook.views.PlayerModelState.PlayerManifestParseFailed
import org.librarysimplified.audiobook.views.focus.PlayerFocusWatcher
import org.librarysimplified.audiobook.views.mediacontrols.PlayerMediaController
import org.librarysimplified.http.api.LSHTTPClientType
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.ServiceLoader
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

object PlayerModel {

  /**
   * The last-read position of the current book when the book was opened.
   */

  @Volatile
  private var playerStartingPosition: PlayerPosition? = null

  val timeTracker =
    PlayerTimeTracker.create()

  @Volatile
  var authorizationHandlerExtensions: List<PlayerAuthorizationHandlerExtensionType> =
    ServiceLoader.load(PlayerAuthorizationHandlerExtensionType::class.java)
      .toList()

  @Volatile
  var bookTitle: String = ""

  @Volatile
  var bookAuthor: String = ""

  @Volatile
  private var coverImageField: Bitmap? = null

  @Volatile
  private lateinit var application: Application

  /**
   * The cover image for the audio book.
   */

  val coverImage: Bitmap?
    get() = this.coverImageField

  private var audioManagerService: AudioManager? = null

  val playbackRate: PlayerPlaybackRate
    get() = try {
      PlayerReference.opPlaybackRate()
    } catch (_: Exception) {
      PlayerPlaybackRate.NORMAL_TIME
    }

  private val logger =
    LoggerFactory.getLogger(PlayerModel::class.java)

  private val downloadExecutor =
    Executors.newFixedThreadPool(1) { r: Runnable ->
      val thread = Thread(r)
      thread.name = "org.librarysimplified.audiobook.views.PlayerModel.downloader-${thread.id}"
      thread.priority = Thread.MIN_PRIORITY
      thread
    }

  private val downloadProvider =
    DownloadProvider.create(this.downloadExecutor)

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
        this.resetLog()
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

  /*
   * Subscribe to the sleep timer so that the player can be paused when the timer expires.
   */

  private val sleepTimerSubscription =
    PlayerSleepTimer.events.subscribe {
      this.onSleepTimerEvent(it)
    }

  @Volatile
  @GuardedBy("databaseRefLock")
  private var databaseRef: ADatabaseType? = null
  private val databaseRefLock: Any = Any()

  init {
    this.stateSubject.onNext(this.state)

    this.stateEvents.ofType(PlayerModelState.PlayerOpen::class.java)
      .subscribe { e ->
        this.timeTracker.bookOpened(e.palaceId)
      }

    this.stateEvents.ofType(PlayerBookOpenFailed::class.java)
      .subscribe {
        this.timeTracker.bookClosed()
      }

    this.stateEvents.ofType(PlayerModelState.PlayerClosed::class.java)
      .subscribe {
        this.timeTracker.bookClosed()
      }

    this.playerEvents.ofType(PlayerEventPlaybackStarted::class.java)
      .subscribe { e ->
        this.timeTracker.bookPlaybackStarted(e.palaceId, this.playbackRate.speed)
      }

    this.playerEvents.ofType(PlayerEventPlaybackPaused::class.java)
      .subscribe { e ->
        this.timeTracker.bookPlaybackPaused(e.palaceId, this.playbackRate.speed)
      }

    this.playerEvents.ofType(PlayerEventPlaybackStopped::class.java)
      .subscribe { e ->
        this.timeTracker.bookPlaybackPaused(e.palaceId, this.playbackRate.speed)
      }

    this.playerEvents.ofType(PlayerEventPlaybackRateChanged::class.java)
      .subscribe { e ->
        this.timeTracker.bookPlaybackRateChanged(e.palaceId, e.rate.speed)
      }
  }

  @UiThread
  fun submitViewCommand(command: PlayerViewCommand) {
    PlayerUIThread.checkIsUIThread()
    this.viewCommandSource.onNext(command)
  }

  private fun downloadManifest(
    strategy: ManifestFulfillmentStrategyType
  ): PlayerResult<ManifestFulfilled, ManifestFulfillmentError> {
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
    licenseChecks: List<SingleLicenseCheckProviderType>,
    httpClient: LSHTTPClientType,
    cacheDir: File
  ): LicenseCheckResult {
    this.logger.debug("checkManifest")

    val check =
      LicenseChecks.createLicenseCheck(
        LicenseCheckParameters(
          manifest = manifest,
          httpClient = httpClient,
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
      return check.execute()
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
    data: ManifestUnparsed
  ): ParseResult<PlayerManifest> {
    this.logger.debug("parseManifest")

    return ManifestParsers.parse(
      uri = source,
      input = data,
      extensions = extensions
    )
  }

  /**
   * Attempt to download and parse the audio book manifest.
   */

  fun downloadParseAndCheckManifest(
    sourceURI: URI,
    httpClient: LSHTTPClientType,
    cacheDir: File,
    palaceID: PlayerPalaceID,
    licenseChecks: List<SingleLicenseCheckProviderType>,
    strategy: ManifestFulfillmentStrategyType,
    parserExtensions: List<ManifestParserExtensionType>,
    bookCredentials: PlayerBookCredentialsType
  ): CompletableFuture<Unit> {
    this.logger.debug("downloadParseAndCheckManifest")

    return this.executeTaskCancellingExisting {
      this.opDownloadAndParseManifest(
        bookCredentials = bookCredentials,
        cacheDir = cacheDir,
        licenseChecks = licenseChecks,
        palaceID = palaceID,
        parserExtensions = parserExtensions,
        sourceURI = sourceURI,
        strategy = strategy,
        httpClient = httpClient,
      )
    }
  }

  /**
   * Attempt to download and parse an LCP license and, from there, a manifest.
   */

  fun downloadParseAndCheckLCPLicense(
    context: Application,
    authorizationHandler: PlayerAuthorizationHandlerType,
    bookCredentials: PlayerBookCredentialsType,
    cacheDir: File,
    httpClient: LSHTTPClientType,
    licenseChecks: List<SingleLicenseCheckProviderType>,
    licenseFile: File,
    licenseFileTemp: File,
    licenseParameters: ManifestFulfillmentBasicParameters,
    palaceID: PlayerPalaceID,
    parserExtensions: List<ManifestParserExtensionType>,
  ): CompletableFuture<Unit> {
    this.logger.debug("downloadParseAndCheckLCPLicense")

    return this.executeTaskCancellingExisting {
      this.opDownloadAndParseLCPLicense(
        authorizationHandler = authorizationHandler,
        bookCredentials = bookCredentials,
        cacheDir = cacheDir,
        context = context,
        licenseChecks = licenseChecks,
        licenseFile = licenseFile,
        licenseFileTemp = licenseFileTemp,
        licenseParameters = licenseParameters,
        palaceID = palaceID,
        parserExtensions = parserExtensions,
        httpClient = httpClient,
      )
    }
  }

  /**
   * Parse an LCP license and manifest.
   */

  fun parseAndCheckLCPLicense(
    bookCredentials: PlayerBookCredentialsType,
    cacheDir: File,
    licenseBytes: ByteArray,
    licenseChecks: List<SingleLicenseCheckProviderType>,
    manifestUnparsed: ManifestUnparsed,
    parserExtensions: List<ManifestParserExtensionType>,
    httpClient: LSHTTPClientType,
  ): CompletableFuture<Unit> {
    this.logger.debug("parseAndCheckLCPLicense")

    return this.executeTaskCancellingExisting {
      this.opParseAndCheckLCPLicense(
        bookCredentials = bookCredentials,
        cacheDir = cacheDir,
        httpClient = httpClient,
        licenseBytes = licenseBytes,
        licenseChecks = licenseChecks,
        manifestUnparsed = manifestUnparsed,
        parserExtensions = parserExtensions,
      )
    }
  }

  private fun opParseAndCheckLCPLicense(
    bookCredentials: PlayerBookCredentialsType,
    cacheDir: File,
    licenseBytes: ByteArray,
    licenseChecks: List<SingleLicenseCheckProviderType>,
    manifestUnparsed: ManifestUnparsed,
    parserExtensions: List<ManifestParserExtensionType>,
    httpClient: LSHTTPClientType,
  ) {
    this.logger.debug("opParseAndCheckLCPLicense")
    this.setNewState(PlayerManifestInProgress)

    try {
      return when (val r = LCPDownloads.parseLicense(licenseBytes)) {
        is PlayerResult.Failure -> {
          this.setNewState(PlayerManifestDownloadFailed(r.failure))
          throw OperationFailedException()
        }

        is PlayerResult.Success -> {
          val licenseFileTemp =
            File(cacheDir, "license.lcpl.tmp")
          val licenseFile =
            File(cacheDir, "license.lcpl")

          licenseFileTemp.delete()
          licenseFileTemp.outputStream().use { output ->
            output.write(licenseBytes)
            output.flush()
          }
          licenseFileTemp.renameTo(licenseFile)

          val parseResult =
            this.parseManifest(
              source = URI.create("urn:unavailable"),
              extensions = parserExtensions,
              data = manifestUnparsed
            )

          if (parseResult is ParseResult.Failure) {
            this.manifestParseErrorLogField = parseResult.errors.toList()
            this.setNewState(PlayerManifestParseFailed(parseResult.errors))
            throw OperationFailedException()
          }

          val (_, parsedManifest) = parseResult as ParseResult.Success
          val checkResult =
            this.checkManifest(
              cacheDir = cacheDir,
              httpClient = httpClient,
              licenseChecks = licenseChecks,
              manifest = parsedManifest,
            )

          if (!checkResult.checkSucceeded()) {
            this.setNewState(PlayerManifestLicenseChecksFailed(checkResult.summarize()))
            throw OperationFailedException()
          }

          this.setNewState(
            PlayerManifestOK(
              manifest = parsedManifest,
              bookSource = PlayerBookSource.PlayerBookSourceLicenseFile(licenseFile),
              bookCredentials = bookCredentials
            )
          )
        }
      }
    } catch (e: OperationFailedException) {
      throw e
    } catch (e: Throwable) {
      this.logger.error("Unexpected exception: ", e)
      this.setNewState(
        PlayerManifestParseFailed(
          listOf(
            ParseError(
              source = URI.create("urn:unavailable"),
              message = e.message ?: e.javaClass.name,
              exception = e
            )
          )
        )
      )
      throw e
    }
  }

  private fun opDownloadAndParseLCPLicense(
    authorizationHandler: PlayerAuthorizationHandlerType,
    bookCredentials: PlayerBookCredentialsType,
    cacheDir: File,
    context: Application,
    httpClient: LSHTTPClientType,
    licenseChecks: List<SingleLicenseCheckProviderType>,
    licenseFile: File,
    licenseFileTemp: File,
    licenseParameters: ManifestFulfillmentBasicParameters,
    palaceID: PlayerPalaceID,
    parserExtensions: List<ManifestParserExtensionType>,
  ) {
    this.logger.debug("opDownloadAndParseLCPLicense")
    this.setNewState(PlayerManifestInProgress)
    PlayerObservableAuthorizationHandler.setHandler(authorizationHandler)

    try {
      val receiver: (ManifestFulfillmentEvent) -> Unit = { event ->
        this.manifestDownloadLogField = this.manifestDownloadLogField.plus(event)
        this.manifestDownloadEventSubject.onNext(event)
      }

      val licenseAndBytes: LCPLicenseAndBytes =
        when (val result = LCPDownloads.downloadLicense(licenseParameters, receiver)) {
          is PlayerResult.Failure -> {
            this.setNewState(PlayerManifestDownloadFailed(result.failure))
            throw OperationFailedException()
          }

          is PlayerResult.Success -> result.result
        }

      licenseFileTemp.delete()
      licenseFileTemp.outputStream().use { output ->
        output.write(licenseAndBytes.licenseBytes)
        output.flush()
      }
      licenseFileTemp.renameTo(licenseFile)

      val manifestData: ManifestFulfilled =
        when (val result = LCPDownloads.downloadManifestFromPublication(
          context = context,
          authorizationHandler = PlayerObservableAuthorizationHandler,
          license = licenseAndBytes.license,
          receiver = receiver
        )) {
          is PlayerResult.Failure -> {
            this.setNewState(PlayerManifestDownloadFailed(result.failure))
            throw OperationFailedException()
          }

          is PlayerResult.Success -> result.result
        }

      return this.opParseManifest(
        bookCredentials = bookCredentials,
        bookSource = PlayerBookSource.PlayerBookSourceLicenseFile(licenseFile),
        cacheDir = cacheDir,
        licenseChecks = licenseChecks,
        manifest = manifestData,
        palaceID = palaceID,
        parserExtensions = parserExtensions,
        sourceURI = manifestData.source ?: URI.create("urn:unavailable"),
        httpClient = httpClient,
      )
    } catch (e: OperationFailedException) {
      throw e
    } catch (e: Throwable) {
      this.logger.error("Unexpected exception: ", e)
      this.setNewState(
        PlayerManifestParseFailed(
          listOf(
            ParseError(
              source = URI.create("urn:unavailable"),
              message = e.message ?: e.javaClass.name,
              exception = e
            )
          )
        )
      )
      throw e
    }
  }

  private fun opDownloadAndParseManifest(
    bookCredentials: PlayerBookCredentialsType,
    cacheDir: File,
    licenseChecks: List<SingleLicenseCheckProviderType>,
    palaceID: PlayerPalaceID,
    parserExtensions: List<ManifestParserExtensionType>,
    sourceURI: URI,
    strategy: ManifestFulfillmentStrategyType,
    httpClient: LSHTTPClientType,
  ) {
    this.logger.debug("opDownloadAndParseManifest")
    this.setNewState(PlayerManifestInProgress)

    try {
      val downloadResult = this.downloadManifest(strategy)
      if (downloadResult is PlayerResult.Failure) {
        this.setNewState(PlayerManifestDownloadFailed(downloadResult.failure))
        throw OperationFailedException()
      }

      val result = (downloadResult as PlayerResult.Success).result

      return this.opParseManifest(
        bookCredentials = bookCredentials,
        bookSource = PlayerBookSource.PlayerBookSourceManifestOnly,
        cacheDir = cacheDir,
        httpClient = httpClient,
        licenseChecks = licenseChecks,
        manifest = result,
        palaceID = palaceID,
        parserExtensions = parserExtensions,
        sourceURI = sourceURI,
      )
    } catch (e: OperationFailedException) {
      throw e
    } catch (e: Throwable) {
      this.logger.error("Unexpected exception: ", e)
      this.setNewState(
        PlayerManifestParseFailed(
          listOf(
            ParseError(
              source = URI.create("urn:unavailable"),
              message = e.message ?: e.javaClass.name,
              exception = e
            )
          )
        )
      )
      throw e
    }
  }

  private fun resetLog() {
    this.manifestDownloadLogField = listOf()
    this.singleLicenseCheckLogField = listOf()
    this.manifestParseErrorLogField = listOf()
  }

  /**
   * Attempt to download and parse an LCP license and, from there, a manifest.
   */

  fun parseAndCheckManifest(
    bookCredentials: PlayerBookCredentialsType,
    cacheDir: File,
    licenseChecks: List<SingleLicenseCheckProviderType>,
    manifest: ManifestFulfilled,
    palaceID: PlayerPalaceID,
    parserExtensions: List<ManifestParserExtensionType>,
    httpClient: LSHTTPClientType,
  ): CompletableFuture<Unit> {
    this.logger.debug("parseAndCheckManifest")

    return this.executeTaskCancellingExisting {
      this.opParseManifest(
        bookCredentials = bookCredentials,
        bookSource = PlayerBookSource.PlayerBookSourceManifestOnly,
        cacheDir = cacheDir,
        httpClient = httpClient,
        licenseChecks = licenseChecks,
        manifest = manifest,
        palaceID = palaceID,
        parserExtensions = parserExtensions,
        sourceURI = URI.create("urn:unavailable"),
      )
    }
  }

  private fun opParseManifest(
    bookCredentials: PlayerBookCredentialsType,
    bookSource: PlayerBookSource,
    cacheDir: File,
    licenseChecks: List<SingleLicenseCheckProviderType>,
    manifest: ManifestFulfilled,
    palaceID: PlayerPalaceID,
    parserExtensions: List<ManifestParserExtensionType>,
    sourceURI: URI,
    httpClient: LSHTTPClientType,
  ) {
    this.logger.debug("opParseManifest")
    this.setNewState(PlayerManifestInProgress)

    try {
      val parseResult =
        this.parseManifest(
          source = sourceURI,
          extensions = parserExtensions,
          data = ManifestUnparsed(palaceID, manifest.data)
        )

      if (parseResult is ParseResult.Failure) {
        this.manifestParseErrorLogField = parseResult.errors.toList()
        this.setNewState(PlayerManifestParseFailed(parseResult.errors))
        throw OperationFailedException()
      }

      val (_, parsedManifest) = parseResult as ParseResult.Success
      val checkResult =
        this.checkManifest(
          cacheDir = cacheDir,
          httpClient = httpClient,
          licenseChecks = licenseChecks,
          manifest = parsedManifest,
        )

      if (!checkResult.checkSucceeded()) {
        this.setNewState(PlayerManifestLicenseChecksFailed(checkResult.summarize()))
        throw OperationFailedException()
      }

      this.setNewState(
        PlayerManifestOK(
          manifest = parsedManifest,
          bookSource = bookSource,
          bookCredentials = bookCredentials
        )
      )
    } catch (e: OperationFailedException) {
      throw e
    } catch (e: Throwable) {
      this.logger.error("Unexpected exception: ", e)
      this.setNewState(
        PlayerManifestParseFailed(
          listOf(
            ParseError(
              source = sourceURI,
              message = e.message ?: e.javaClass.name,
              exception = e
            )
          )
        )
      )
      throw e
    }
  }

  fun openPlayerForManifest(
    context: Application,
    httpClient: LSHTTPClientType,
    manifest: PlayerManifest,
    playerID: UUID,
    fetchAll: Boolean,
    bookCredentials: PlayerBookCredentialsType,
    bookSource: PlayerBookSource,
    authorizationHandler: PlayerAuthorizationHandlerType
  ): CompletableFuture<Unit> {
    this.logger.debug("openPlayerForManifest")

    if (this.authorizationHandlerExtensions.isEmpty()) {
      this.logger.debug("openPlayerForManifest: No authorization extensions were provided.")
    } else {
      this.authorizationHandlerExtensions.forEachIndexed { index, extension ->
        this.logger.debug("openPlayerForManifest: [{}] Extension {}", index, extension.name)
      }
    }

    this.audioManagerService =
      context.getSystemService<AudioManager>()

    return this.executeTaskCancellingExisting {
      this.opOpenPlayerForManifest(
        authorizationHandlerDelegate = authorizationHandler,
        authorizationHandlerExtensions = this.authorizationHandlerExtensions,
        bookCredentials = bookCredentials,
        bookSource = bookSource,
        context = context,
        fetchAll = fetchAll,
        httpClient = httpClient,
        manifest = manifest,
        playerID = playerID,
      )
    }
  }

  private fun opOpenPlayerForManifest(
    manifest: PlayerManifest,
    playerID: UUID,
    httpClient: LSHTTPClientType,
    context: Application,
    fetchAll: Boolean,
    bookCredentials: PlayerBookCredentialsType,
    bookSource: PlayerBookSource,
    authorizationHandlerDelegate: PlayerAuthorizationHandlerType,
    authorizationHandlerExtensions: List<PlayerAuthorizationHandlerExtensionType>,
  ) {
    this.logger.debug("opOpenPlayerForManifest")

    val authorizationHandler =
      PlayerAuthorizationHandlerDelegating.create(
        delegate = authorizationHandlerDelegate,
        extensions = authorizationHandlerExtensions
      )
    PlayerObservableAuthorizationHandler.setHandler(authorizationHandler)
    PlayerReference.opPlayerClose()

    this.openDatabase(context.filesDir.toPath().resolve("palace-audiobook-persistence.db"))

    val bookID =
      PlayerBookID.transform(manifest.metadata.identifier)
    this.playerStartingPosition =
      this.lastReadPositionGet(bookID)

    /*
     * Ask the API for the best audio engine available that can handle the given manifest.
     */

    val engine =
      PlayerAudioEngines.findBestFor(
        PlayerAudioEngineRequest(
          manifest = manifest,
          filter = { true },
          downloadProvider = this.downloadProvider,
          httpClient = httpClient,
          bookCredentials = bookCredentials,
          bookSource = bookSource,
          authorizationHandler = PlayerObservableAuthorizationHandler
        )
      )

    if (engine == null) {
      this.setNewState(
        PlayerBookOpenFailed(
          message = "No suitable audio engine for manifest.",
          exception = UnsupportedOperationException(),
          extraMessages = listOf()
        )
      )
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
        authorizationHandler = PlayerObservableAuthorizationHandler,
      )

    if (bookResult is PlayerResult.Failure) {
      this.logger.error("Book failed to open: ", bookResult.failure)
      this.setNewState(
        PlayerBookOpenFailed(
          message = bookResult.failure.message ?: bookResult.failure.javaClass.name,
          exception = bookResult.failure,
          extraMessages = listOf()
        )
      )
      throw OperationFailedException()
    }

    val newBook =
      (bookResult as PlayerResult.Success).result
    val newPlayer =
      newBook.createPlayer(playerID)
    val newPair =
      PlayerBookAndPlayer(newBook, newPlayer)

    newPlayer.events.subscribe(
      { event -> this.playerEventSubject.onNext(event) },
      { exception -> this.logger.error("Player exception: ", exception) }
    )

    newPlayer.events.subscribe(
      { event -> this.onHandleChapterCompletionForSleepTimer(event) },
      { exception -> this.logger.error("Player exception: ", exception) }
    )

    newBook.readingOrderElementDownloadStatus.subscribe(
      { event -> this.downloadEventSubject.onNext(event) },
      { exception -> this.logger.error("Download exception: ", exception) }
    )

    newPlayer.events.subscribe(
      { event -> this.onHandleSaveLastReadPosition(bookID, event) },
      { exception -> this.logger.error("Player exception: ", exception) }
    )

    if (fetchAll) {
      newPair.audioBook.wholeBookDownloadTask.fetch()
    }

    val startingPosition = this.playerStartingPosition
    if (startingPosition != null) {
      try {
        newPair.player.movePlayheadToLocation(startingPosition)
      } catch (e: Throwable) {
        this.logger.debug("Failed to set starting position: ", e)
      }
    }

    PlayerReference.opNewPlayer(newPair)

    this.setNewState(
      PlayerModelState.PlayerOpen(
        palaceId = newPair.audioBook.palaceId,
        positionOnOpen = this.playerStartingPosition
      )
    )

    PlayerUIThread.runOnUIThread {
      try {
        PlayerMediaController.start(this.application)
      } catch (e: Throwable) {
        this.logger.debug("Failed to start media controller: ", e)
      }
    }
  }

  private fun onHandleSaveLastReadPosition(
    bookID: PlayerBookID,
    event: PlayerEvent
  ) {
    when (event) {
      is PlayerEvent.PlayerEventWithPosition.PlayerEventCreateBookmark -> {
        when (event.kind) {
          PlayerBookmarkKind.EXPLICIT -> {
            // Do nothing.
          }

          PlayerBookmarkKind.LAST_READ -> {
            this.lastReadPositionSet(
              bookID,
              event.readingOrderItem,
              event.readingOrderItemOffsetMilliseconds
            )
          }
        }
      }

      else -> {
        // Do nothing.
      }
    }
  }

  private fun lastReadPositionSet(
    bookID: PlayerBookID,
    readingOrderItem: PlayerReadingOrderItemType,
    readingOrderItemOffsetMilliseconds: PlayerMillisecondsReadingOrderItem
  ) {
    val position =
      PlayerPosition(
        readingOrderID = readingOrderItem.id,
        offsetMilliseconds = readingOrderItemOffsetMilliseconds
      )
    this.database()
      ?.lastReadPositionSave(bookID, position)
  }

  private fun lastReadPositionGet(
    bookID: PlayerBookID
  ): PlayerPosition? {
    return this.database()
      ?.lastReadPositionGet(bookID)
      ?.orElse(null)
  }

  private fun database(): ADatabaseType? {
    return synchronized(this.databaseRefLock) {
      this.databaseRef
    }
  }

  private fun openDatabase(file: Path) {
    synchronized(this.databaseRefLock) {
      if (this.databaseRef == null) {
        try {
          this.logger.debug("Opening persistence database...")
          this.databaseRef = ADatabases.open(file)
          this.logger.debug("Opened persistence database.")
        } catch (e: Throwable) {
          this.logger.debug("Failed to open persistence database: ", e)
        }
      }
    }
  }

  private fun onHandleChapterCompletionForSleepTimer(
    event: PlayerEvent
  ) {
    return when (event) {
      is PlayerEventChapterCompleted -> {
        when (PlayerSleepTimer.configuration) {
          PlayerSleepTimerConfiguration.EndOfChapter -> {
            this.logger.debug("Chapter finished; completing sleep timer now.")
            PlayerSleepTimer.finish()
          }

          PlayerSleepTimerConfiguration.Off,
          is PlayerSleepTimerConfiguration.WithDuration -> {
            // Nothing to do.
          }
        }
      }

      else -> {
        // Nothing to do.
      }
    }
  }

  fun closeBookOrDismissError(): CompletableFuture<Unit> {
    return this.executeTaskCancellingExisting {
      this.opCloseBookOrDismissError()
    }
  }

  private fun opCloseBookOrDismissError() {
    this.currentFuture?.cancel(true)

    /*
     * Stop any downloads.
     */

    this.downloadProvider.cancelAll()
    PlayerReference.onDownloadCancelAll()

    /*
     * Stop the focus watcher.
     */

    PlayerUIThread.runOnUIThread {
      try {
        PlayerFocusWatcher.disable()
      } catch (e: Throwable) {
        this.logger.debug("Failed to stop media controller: ", e)
      }
    }

    /*
     * Stop the media controller.
     */

    PlayerUIThread.runOnUIThread {
      try {
        PlayerMediaController.stop()
      } catch (e: Throwable) {
        this.logger.debug("Failed to stop media controller: ", e)
      }
    }

    /*
     * Cancel the sleep timer.
     */

    try {
      PlayerSleepTimer.cancel()
    } catch (e: Throwable) {
      this.logger.error("Failed to cancel sleep timer: ", e)
    }

    /*
     * Clear the bookmark list.
     */

    try {
      PlayerBookmarkModel.clearBookmarks()
    } catch (e: Throwable) {
      this.logger.error("Failed to clear bookmarks: ", e)
    }

    /*
     * Drop the title and cover.
     */

    PlayerUIThread.runOnUIThread {
      this.bookTitle = ""
      this.setCoverImage(null)
    }

    PlayerReference.opPlayerClose()
    this.setNewState(PlayerModelState.PlayerClosed)
  }

  private fun setNewState(newState: PlayerModelState) {
    this.stateField = newState
    this.stateSubject.onNext(newState)
  }

  private fun onSleepTimerEvent(
    event: PlayerSleepTimerEvent
  ) {
    try {
      return when (event) {
        PlayerSleepTimerEvent.PlayerSleepTimerFinished -> {
          this.logger.debug("Sleep timer finished: Pausing player")
          PlayerReference.opPause(PlayerPauseReason.PAUSE_REASON_SLEEP_TIMER)
          Unit
        }

        is PlayerSleepTimerEvent.PlayerSleepTimerStatusChanged -> {
          // Nothing to do
        }
      }
    } catch (e: Exception) {
      this.logger.error("onSleepTimerEvent: ", e)
    }
  }

  fun play() {
    try {
      PlayerReference.opPlay()

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
    } catch (e: Exception) {
      this.logger.error("play: ", e)
    }
  }

  fun pause(
    reason: PlayerPauseReason
  ) {
    PlayerReference.opPause(reason)

    try {
      PlayerSleepTimer.pause()
    } catch (e: Exception) {
      this.logger.error("Pause: ", e)
    }
  }

  fun playOrPauseAsAppropriate(
    reason: PlayerPauseReason
  ) {
    PlayerReference.opPlayOrPauseAsAppropriate(reason)
  }

  fun skipForward() {
    PlayerReference.opSkipPlayhead(this.seekIncrement())
  }

  fun skipBack() {
    PlayerReference.opSkipPlayhead(-this.seekIncrement())
  }

  fun movePlayheadTo(playerPosition: PlayerPosition) {
    PlayerReference.opMovePlayheadTo(playerPosition)
  }

  fun movePlayheadToAbsoluteTime(newOffset: PlayerMillisecondsAbsolute) {
    PlayerReference.opMovePlayheadToAbsoluteTime(newOffset)
  }

  fun manifest(): PlayerManifest? {
    return PlayerReference.opManifest()
  }

  fun bookmarkCreate() {
    PlayerReference.opBookmarkCreate()
  }

  fun bookmarkDelete(bookmark: PlayerBookmark) {
    PlayerReference.opBookmarkDelete(bookmark)
  }

  fun setPlaybackRate(rate: PlayerPlaybackRate) {
    PlayerReference.opPlaybackRateSet(rate)
  }

  val isPlaying: Boolean
    get() = PlayerReference.opIsPlaying()

  fun setCoverImage(image: Bitmap?) {
    this.coverImageField = image
    this.submitViewCommand(PlayerViewCommand.PlayerViewCoverImageChanged)
  }

  val isBuffering: Boolean
    get() = PlayerReference.opIsBuffering()

  fun isDownloading(): Boolean {
    return PlayerReference.opIsDownloading()
  }

  fun isDownloadingCompleted(): Boolean {
    return PlayerReference.opIsDownloadingCompleted()
  }

  fun isAnyDownloadingFailed(): Boolean {
    return PlayerReference.opIsDownloadingAnyFailed()
  }

  fun isStreamingSupported(): Boolean {
    return PlayerReference.opIsStreamingSupported()
  }

  fun start(
    application: Application
  ) {
    this.application = application
  }

  fun seekIncrement(): Long {
    return 30_000L
  }

  fun chapterTitleFor(
    position: PlayerPosition
  ): String {
    return PlayerReference.opChapterTitleFor(position)
  }

  /**
   * Process the given book file: Attempt to extract a manifest and parse it, and take ownership
   * of the book file.
   */

  @Deprecated("Packaged audiobooks are deprecated; use streaming.")
  fun downloadLocalPackagedAudiobook(
    context: Application,
    bookCredentials: PlayerBookCredentialsType,
    bookFile: File,
    cacheDir: File,
    licenseChecks: List<SingleLicenseCheckProviderType>,
    palaceID: PlayerPalaceID,
    parserExtensions: List<ManifestParserExtensionType>,
    httpClient: LSHTTPClientType,
  ): CompletableFuture<Unit> {
    this.logger.debug("downloadLocalPackagedAudiobook")

    return this.executeTaskCancellingExisting {
      this.opDownloadLocalPackagedAudiobook(
        bookCredentials = bookCredentials,
        bookFile = bookFile,
        cacheDir = cacheDir,
        context = context,
        licenseChecks = licenseChecks,
        palaceID = palaceID,
        parserExtensions = parserExtensions,
        httpClient = httpClient,
      )
    }
  }

  private fun opDownloadLocalPackagedAudiobook(
    context: Application,
    bookCredentials: PlayerBookCredentialsType,
    bookFile: File,
    cacheDir: File,
    licenseChecks: List<SingleLicenseCheckProviderType>,
    palaceID: PlayerPalaceID,
    parserExtensions: List<ManifestParserExtensionType>,
    httpClient: LSHTTPClientType,
  ) {
    this.logger.debug("opDownloadLocalPackagedAudiobook")
    this.setNewState(PlayerManifestInProgress)

    try {
      val receiver: (ManifestFulfillmentEvent) -> Unit = { event ->
        this.manifestDownloadLogField = this.manifestDownloadLogField.plus(event)
        this.manifestDownloadEventSubject.onNext(event)
      }

      when (val manifest = LCPDownloads.extractManifestFromFile(
        context = context,
        bookFile = bookFile,
        receiver = receiver
      )) {
        is PlayerResult.Failure -> {
          this.setNewState(PlayerManifestDownloadFailed(manifest.failure))
          throw OperationFailedException()
        }

        is PlayerResult.Success -> {
          val parseResult =
            this.parseManifest(
              source = bookFile.toURI(),
              extensions = parserExtensions,
              data = ManifestUnparsed(palaceID, manifest.result.data)
            )

          if (parseResult is ParseResult.Failure) {
            this.manifestParseErrorLogField = parseResult.errors.toList()
            this.setNewState(PlayerManifestParseFailed(parseResult.errors))
            throw OperationFailedException()
          }

          val (_, parsedManifest) = parseResult as ParseResult.Success
          val checkResult =
            this.checkManifest(
              manifest = parsedManifest,
              httpClient = httpClient,
              licenseChecks = licenseChecks,
              cacheDir = cacheDir
            )

          if (!checkResult.checkSucceeded()) {
            this.setNewState(PlayerManifestLicenseChecksFailed(checkResult.summarize()))
            throw OperationFailedException()
          }

          this.setNewState(
            PlayerManifestOK(
              manifest = parsedManifest,
              bookSource = PlayerBookSource.PlayerBookSourcePackagedBook(bookFile),
              bookCredentials = bookCredentials
            )
          )
        }
      }
    } catch (e: OperationFailedException) {
      throw e
    } catch (e: Throwable) {
      this.logger.error("Unexpected exception: ", e)
      this.setNewState(
        PlayerManifestDownloadFailed(
          ManifestFulfillmentError(
            message = e.message ?: e.javaClass.name,
            extraMessages = listOf(),
            serverData = null
          )
        )
      )
      throw e
    }
  }

  fun downloadAll() {
    PlayerReference.opDownloadAll()
  }

  fun findDownloadingProgressIfAny(): PlayerDownloadProgress? {
    return PlayerReference.opFindDownloadingProgressIfAny()
  }

  fun findDownloadingStatusIfAny(): PlayerDownloadTaskStatus.Downloading? {
    return PlayerReference.opFindDownloadingStatusIfAny()
  }

  fun downloadProgress(): PlayerDownloadProgress {
    return PlayerReference.opDownloadProgress()
  }

  fun isOpen(): Boolean {
    return PlayerReference.opIsOpen()
  }

  fun tableOfContents(): PlayerManifestTOC? {
    return PlayerReference.opTableOfContent()
  }

  fun readingOrder(): List<PlayerReadingOrderItemType> {
    return PlayerReference.opReadingOrder()
  }

  fun readingOrderByID(): Map<PlayerManifestReadingOrderID, PlayerReadingOrderItemType> {
    return PlayerReference.opReadingOrderByID()
  }

  fun downloadTasksFailed(): List<PlayerDownloadTaskType> {
    return PlayerReference.opDownloadTasksFailed()
  }

  fun playerID(): UUID? {
    return PlayerReference.opPlayerID()
  }

  fun chapterPrevious() {
    PlayerReference.opChapterPrevious()
  }

  fun chapterNext() {
    PlayerReference.opChapterNext()
  }
}
