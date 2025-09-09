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
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerAudioEngineRequest
import org.librarysimplified.audiobook.api.PlayerAudioEngines
import org.librarysimplified.audiobook.api.PlayerBookCredentialsType
import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerBookSource
import org.librarysimplified.audiobook.api.PlayerBookmark
import org.librarysimplified.audiobook.api.PlayerBookmarkKind
import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus
import org.librarysimplified.audiobook.api.PlayerEvent
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventPlaybackRateChanged
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventChapterCompleted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackPaused
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStarted
import org.librarysimplified.audiobook.api.PlayerEvent.PlayerEventWithPosition.PlayerEventPlaybackStopped
import org.librarysimplified.audiobook.api.PlayerPlaybackIntention
import org.librarysimplified.audiobook.api.PlayerPlaybackRate
import org.librarysimplified.audiobook.api.PlayerPlaybackStatus
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerSleepTimer
import org.librarysimplified.audiobook.api.PlayerSleepTimerConfiguration
import org.librarysimplified.audiobook.api.PlayerSleepTimerEvent
import org.librarysimplified.audiobook.api.PlayerSleepTimerType
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.api.extensions.PlayerExtensionType
import org.librarysimplified.audiobook.downloads.DownloadProvider
import org.librarysimplified.audiobook.lcp.downloads.LCPDownloads
import org.librarysimplified.audiobook.lcp.downloads.LCPLicenseAndBytes
import org.librarysimplified.audiobook.license_check.api.LicenseCheckParameters
import org.librarysimplified.audiobook.license_check.api.LicenseCheckResult
import org.librarysimplified.audiobook.license_check.api.LicenseChecks
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckProviderType
import org.librarysimplified.audiobook.license_check.spi.SingleLicenseCheckStatus
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
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
import org.librarysimplified.audiobook.views.mediacontrols.PlayerMediaController
import org.librarysimplified.audiobook.views.mediacontrols.PlayerServiceCommand
import org.librarysimplified.http.api.LSHTTPAuthorizationType
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.ServiceLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

object PlayerModel {

  /**
   * The last-read position of the current book when the book was opened.
   */

  @Volatile
  private var playerStartingPosition: PlayerPosition? = null

  @Volatile
  private var isStreamingPermitted: Boolean = false

  val timeTracker =
    PlayerTimeTracker.create()

  @Volatile
  var playerExtensions: List<PlayerExtensionType> =
    ServiceLoader.load(PlayerExtensionType::class.java)
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
      this.playerAndBookField?.player?.playbackRate ?: PlayerPlaybackRate.NORMAL_TIME
    } catch (e: Exception) {
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

  /*
   * A source of player service commands. Note that this is specifically NOT a behavior subject;
   * if nothing is subscribed at the time a command is published, the command is discarded.
   */

  private val playerServiceSubject =
    PublishSubject.create<PlayerServiceCommand>()
      .toSerialized()

  val playerServiceCommands: Observable<PlayerServiceCommand> =
    this.playerServiceSubject.observeOn(AndroidSchedulers.mainThread())

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
        this.timeTracker.bookOpened(e.player.audioBook.palaceId)
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
    userAgent: PlayerUserAgent,
    licenseChecks: List<SingleLicenseCheckProviderType>,
    cacheDir: File
  ): LicenseCheckResult {
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
    userAgent: PlayerUserAgent,
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
        userAgent = userAgent,
      )
    }
  }

  /**
   * Attempt to download and parse an LCP license and, from there, a manifest.
   */

  fun downloadParseAndCheckLCPLicense(
    context: Application,
    bookCredentials: PlayerBookCredentialsType,
    cacheDir: File,
    licenseChecks: List<SingleLicenseCheckProviderType>,
    licenseFile: File,
    licenseFileTemp: File,
    licenseParameters: ManifestFulfillmentBasicParameters,
    palaceID: PlayerPalaceID,
    parserExtensions: List<ManifestParserExtensionType>,
    userAgent: PlayerUserAgent,
  ): CompletableFuture<Unit> {
    this.logger.debug("downloadParseAndCheckLCPLicense")

    return this.executeTaskCancellingExisting {
      this.opDownloadAndParseLCPLicense(
        bookCredentials = bookCredentials,
        cacheDir = cacheDir,
        context = context,
        licenseChecks = licenseChecks,
        licenseFile = licenseFile,
        licenseFileTemp = licenseFileTemp,
        licenseParameters = licenseParameters,
        palaceID = palaceID,
        parserExtensions = parserExtensions,
        userAgent = userAgent,
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
    userAgent: PlayerUserAgent,
  ): CompletableFuture<Unit> {
    this.logger.debug("parseAndCheckLCPLicense")

    return this.executeTaskCancellingExisting {
      this.opParseAndCheckLCPLicense(
        bookCredentials = bookCredentials,
        cacheDir = cacheDir,
        licenseBytes = licenseBytes,
        licenseChecks = licenseChecks,
        manifestUnparsed = manifestUnparsed,
        parserExtensions = parserExtensions,
        userAgent = userAgent
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
    userAgent: PlayerUserAgent,
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
              manifest = parsedManifest,
              userAgent = userAgent,
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
    context: Application,
    bookCredentials: PlayerBookCredentialsType,
    cacheDir: File,
    licenseChecks: List<SingleLicenseCheckProviderType>,
    licenseFile: File,
    licenseFileTemp: File,
    licenseParameters: ManifestFulfillmentBasicParameters,
    palaceID: PlayerPalaceID,
    parserExtensions: List<ManifestParserExtensionType>,
    userAgent: PlayerUserAgent,
  ) {
    this.logger.debug("opDownloadAndParseLCPLicense")
    this.setNewState(PlayerManifestInProgress)

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
          credentials = licenseParameters.credentials,
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
        userAgent = userAgent,
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
    userAgent: PlayerUserAgent,
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
      this.configurePlayerExtensions(result.authorization)

      return this.opParseManifest(
        bookCredentials = bookCredentials,
        bookSource = PlayerBookSource.PlayerBookSourceManifestOnly,
        cacheDir = cacheDir,
        licenseChecks = licenseChecks,
        manifest = result,
        palaceID = palaceID,
        parserExtensions = parserExtensions,
        sourceURI = sourceURI,
        userAgent = userAgent
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
    userAgent: PlayerUserAgent,
  ): CompletableFuture<Unit> {
    this.logger.debug("parseAndCheckManifest")

    return this.executeTaskCancellingExisting {
      this.opParseManifest(
        bookCredentials = bookCredentials,
        bookSource = PlayerBookSource.PlayerBookSourceManifestOnly,
        cacheDir = cacheDir,
        licenseChecks = licenseChecks,
        manifest = manifest,
        palaceID = palaceID,
        parserExtensions = parserExtensions,
        sourceURI = URI.create("urn:unavailable"),
        userAgent = userAgent,
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
    userAgent: PlayerUserAgent,
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
          manifest = parsedManifest,
          userAgent = userAgent,
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
    manifest: PlayerManifest,
    fetchAll: Boolean,
    bookCredentials: PlayerBookCredentialsType,
    bookSource: PlayerBookSource
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
        extensions = this.playerExtensions,
        fetchAll = fetchAll,
        bookCredentials = bookCredentials,
        bookSource = bookSource
      )
    }
  }

  private fun opOpenPlayerForManifest(
    manifest: PlayerManifest,
    userAgent: PlayerUserAgent,
    context: Application,
    extensions: List<PlayerExtensionType>,
    fetchAll: Boolean,
    bookCredentials: PlayerBookCredentialsType,
    bookSource: PlayerBookSource
  ) {
    this.logger.debug("opOpenPlayerForManifest")

    val existingPlayer = this.playerAndBookField
    if (existingPlayer != null) {
      this.logger.debug("Closing old player instance")
      existingPlayer.close()
    } else {
      this.logger.debug("No existing player instance is present")
    }
    this.playerAndBookField = null

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
          downloadProvider = DownloadProvider.create(this.downloadExecutor),
          userAgent = userAgent,
          bookCredentials = bookCredentials,
          bookSource = bookSource
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
        extensions = extensions
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
      newBook.createPlayer()
    val newPair =
      PlayerBookAndPlayer(newBook, newPlayer)

    newPlayer.isStreamingPermitted = this.isStreamingPermitted
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
      PlayerUIThread.runOnUIThread {
        newPair.player.movePlayheadToLocation(startingPosition)
      }
    }

    this.playerAndBookField = newPair

    this.setNewState(
      PlayerModelState.PlayerOpen(
        player = newPair,
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
     * Tell any services to shut down.
     */

    try {
      this.playerServiceSubject.onNext(PlayerServiceCommand.PlayerServiceShutDown)
    } catch (e: Throwable) {
      this.logger.debug("Failed to submit service shutdown command: ", e)
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

    try {
      this.playerAndBookField?.close()
    } catch (e: Throwable) {
      this.logger.error("Failed to close player: ", e)
    }

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
          this.playerAndBookField?.player?.pause()
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
    } catch (e: Exception) {
      this.logger.error("play: ", e)
    }
  }

  fun pause() {
    try {
      this.playerAndBookField?.player?.pause()
    } catch (e: Exception) {
      this.logger.error("Pause: ", e)
    }

    try {
      PlayerSleepTimer.pause()
    } catch (e: Exception) {
      this.logger.error("Pause: ", e)
    }
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

  fun skipForward() {
    try {
      this.playerAndBookField?.player?.skipPlayhead(this.seekIncrement())
    } catch (e: Exception) {
      this.logger.error("skipForward: ", e)
    }
  }

  fun skipBack() {
    try {
      this.playerAndBookField?.player?.skipPlayhead(-this.seekIncrement())
    } catch (e: Exception) {
      this.logger.error("skipBack: ", e)
    }
  }

  fun book(): PlayerAudioBookType? {
    return this.playerAndBookField?.audioBook
  }

  fun movePlayheadTo(playerPosition: PlayerPosition) {
    try {
      this.playerAndBookField?.player?.movePlayheadToLocation(playerPosition)
    } catch (e: Exception) {
      this.logger.error("movePlayheadTo: ", e)
    }
  }

  fun movePlayheadToAbsoluteTime(newOffset: PlayerMillisecondsAbsolute) {
    try {
      this.playerAndBookField?.player?.movePlayheadToAbsoluteTime(newOffset)
    } catch (e: Exception) {
      this.logger.error("movePlayheadToAbsoluteTime: ", e)
    }
  }

  fun manifest(): PlayerManifest? {
    return this.playerAndBookField?.audioBook?.manifest
  }

  fun bookmarkCreate() {
    try {
      this.playerAndBookField?.player?.bookmark()
    } catch (e: Exception) {
      this.logger.error("bookmarkCreate: ", e)
    }
  }

  fun bookmarkDelete(bookmark: PlayerBookmark) {
    try {
      this.playerAndBookField?.player?.bookmarkDelete(bookmark)
    } catch (e: Exception) {
      this.logger.error("bookmarkDelete: ", e)
    }
  }

  fun setPlaybackRate(item: PlayerPlaybackRate) {
    try {
      this.playerAndBookField?.player?.playbackRate = item
    } catch (e: Exception) {
      this.logger.error("setPlaybackRate: ", e)
    }
  }

  val isPlaying: Boolean
    get() = this.playerAndBookField?.player?.playbackStatus != PlayerPlaybackStatus.PAUSED

  fun setCoverImage(image: Bitmap?) {
    this.coverImageField = image
    this.submitViewCommand(PlayerViewCommand.PlayerViewCoverImageChanged)
  }

  val isBuffering: Boolean
    get() = this.playerAndBookField?.player?.playbackStatus == PlayerPlaybackStatus.BUFFERING

  fun isDownloading(): Boolean {
    return try {
      val book = this.playerAndBookField?.audioBook
      if (book != null) {
        return book.downloadTasks.any { t -> t.status is PlayerDownloadTaskStatus.Downloading }
      } else {
        false
      }
    } catch (e: Exception) {
      this.logger.error("isDownloading: ", e)
      false
    }
  }

  fun isDownloadingCompleted(): Boolean {
    return try {
      val book = this.playerAndBookField?.audioBook
      if (book != null) {
        return book.downloadTasks.all { t -> t.status is PlayerDownloadTaskStatus.IdleDownloaded }
      } else {
        false
      }
    } catch (e: Exception) {
      this.logger.error("isDownloadingCompleted: ", e)
      false
    }
  }

  fun isAnyDownloadingFailed(): Boolean {
    return try {
      val book = this.playerAndBookField?.audioBook
      if (book != null) {
        return book.downloadTasks.any { t -> t.status is PlayerDownloadTaskStatus.Failed }
      } else {
        false
      }
    } catch (e: Exception) {
      this.logger.error("isAnyDownloadingFailed: ", e)
      false
    }
  }

  fun isStreamingSupportedAndPermitted(): Boolean {
    return try {
      val playerAndBook = this.playerAndBookField
      if (playerAndBook != null) {
        return playerAndBook.audioBook.supportsStreaming && playerAndBook.player.isStreamingPermitted
      } else {
        false
      }
    } catch (e: Exception) {
      this.logger.error("isStreamingSupportedAndPermitted: ", e)
      false
    }
  }

  fun start(
    application: Application
  ) {
    this.application = application
  }

  fun setStreamingPermitted(
    permitted: Boolean
  ) {
    return try {
      this.logger.debug("setStreamingPermitted: {}", permitted)
      this.isStreamingPermitted = permitted

      val playerAndBook = this.playerAndBookField
      if (playerAndBook != null) {
        playerAndBook.player.isStreamingPermitted = permitted
      } else {
        Unit
      }
    } catch (e: Exception) {
      this.logger.error("setStreamingPermitted: ", e)
    }
  }

  fun seekIncrement(): Long {
    return 30_000L
  }

  fun chapterTitleFor(
    position: PlayerPosition
  ): String {
    return try {
      val book = this.playerAndBookField?.audioBook
      if (book != null) {
        val item =
          book.tableOfContents.lookupTOCItem(
            id = position.readingOrderID,
            offset = position.offsetMilliseconds
          )
        return item.title
      } else {
        ""
      }
    } catch (e: Exception) {
      this.logger.error("chapterTitleFor: ", e)
      ""
    }
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
    userAgent: PlayerUserAgent,
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
        userAgent = userAgent,
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
    userAgent: PlayerUserAgent,
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
              userAgent = userAgent,
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
}
