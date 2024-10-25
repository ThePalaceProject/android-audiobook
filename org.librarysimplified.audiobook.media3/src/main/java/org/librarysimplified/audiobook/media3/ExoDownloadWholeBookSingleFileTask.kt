package org.librarysimplified.audiobook.media3

import net.jcip.annotations.GuardedBy
import org.librarysimplified.audiobook.api.PlayerDownloadProgress
import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus
import org.librarysimplified.audiobook.api.PlayerDownloadTaskType
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.lcp.downloads.LCPDownloads
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference

internal class ExoDownloadWholeBookSingleFileTask(
  override val index: Int,
  private val readingOrderItem: ExoReadingOrderItemHandle,
  private val sharedState: SharedState,
  override val playbackURI: URI
) : PlayerDownloadTaskType {

  private val logger =
    LoggerFactory.getLogger(ExoDownloadWholeBookSingleFileTask::class.java)

  internal sealed class State {
    data object Initial : State()
    data class Failed(
      val message: String,
      val exception: Exception
    ) : State()

    data object Downloaded : State()
    data class Downloading(val future: CompletableFuture<Unit>) : State()
  }

  internal class SharedState(
    internal val downloadStatusExecutor: ExecutorService,
    internal val downloadProvider: PlayerDownloadProviderType,
    internal val userAgent: PlayerUserAgent,
    internal val bookDownloadURI: URI,
    internal val bookFile: File,
    internal val bookFileTemp: File,
    internal val licenseBytes: ByteArray
  ) {
    var downloading: CompletableFuture<Unit>? = null
    private val stateLock: Any = Object()
    private val progressValue = AtomicReference(PlayerDownloadProgress(0.0))

    @GuardedBy("stateLock")
    private var state: State =
      when (this.bookFile.isFile) {
        true -> {
          this.progressValue.set(PlayerDownloadProgress(1.0))
          State.Downloaded
        }

        false -> {
          this.progressValue.set(PlayerDownloadProgress(0.0))
          State.Initial
        }
      }

    internal fun progress(): PlayerDownloadProgress {
      return this.progressValue.get()
    }

    internal fun stateCurrent(): State {
      return synchronized(this.stateLock) { this.state }
    }

    internal fun stateSet(newState: State) {
      synchronized(this.stateLock) {
        this.state = newState
      }
    }

    internal fun progressSet(progress: PlayerDownloadProgress) {
      this.progressValue.set(progress)
    }
  }

  init {
    this.onBroadcastState()
  }

  override val status: PlayerDownloadTaskStatus
    get() = when (val s = this.stateGetCurrent()) {
      State.Downloaded -> PlayerDownloadTaskStatus.IdleDownloaded
      is State.Downloading -> PlayerDownloadTaskStatus.Downloading(
        if (this.progress.value == 0.0) {
          null
        } else {
          this.progress
        }
      )

      State.Initial -> PlayerDownloadTaskStatus.IdleNotDownloaded
      is State.Failed -> PlayerDownloadTaskStatus.Failed(s.message, s.exception)
    }

  private fun stateGetCurrent() =
    this.sharedState.stateCurrent()

  override fun fetch() {
    this.logger.debug("[{}] fetch", this.readingOrderItem.id)

    when (this.stateGetCurrent()) {
      State.Initial -> {
        this.sharedState.downloading = this.onStartDownload()
      }

      State.Downloaded -> {
        this.onDownloaded()
      }

      is State.Downloading -> {
        this.onDownloading(this.progress)
      }

      is State.Failed -> {
        this.sharedState.downloading = this.onStartDownload()
      }
    }
  }

  override fun cancel() {
    this.logger.debug("[{}] cancel", this.readingOrderItem.id)
    this.sharedState.downloading?.cancel(true)
  }

  override fun delete() {
    this.logger.debug("[{}] delete", this.readingOrderItem.id)

    return when (val current = this.stateGetCurrent()) {
      State.Initial -> this.onBroadcastState()
      State.Downloaded -> this.onDeleteDownloaded()
      is State.Downloading -> this.onDeleteDownloading(current)
      is State.Failed -> this.onBroadcastState()
    }
  }

  override val progress: PlayerDownloadProgress
    get() = this.sharedState.progress()

  override val readingOrderItems: List<PlayerReadingOrderItemType>
    get() = listOf(this.readingOrderItem)

  private fun onBroadcastState() {
    when (val s = this.stateGetCurrent()) {
      State.Initial -> this.onNotDownloaded()
      State.Downloaded -> this.onDownloaded()
      is State.Downloading -> this.onDownloading(this.progress)
      is State.Failed -> this.onDownloadFailed(s.exception)
    }
  }

  private fun onDeleteDownloaded() {
    this.logger.debug("onDeleteDownloaded")

    try {
      ExoFileIO.fileDelete(this.sharedState.bookFile)
      ExoFileIO.fileDelete(this.sharedState.bookFileTemp)
    } catch (e: Exception) {
      this.logger.error("Failed to delete file: ", e)
    } finally {
      this.sharedState.stateSet(State.Initial)
      this.onBroadcastState()
    }
  }

  private fun onDeleteDownloading(
    state: State.Downloading
  ) {
    this.logger.debug("onDeleteDownloading")

    state.future.cancel(true)
    this.sharedState.stateSet(State.Initial)
    this.onDeleteDownloaded()
  }

  private fun onDownloadFailed(
    exception: Exception
  ) {
    this.logger.debug("onDownloadFailed: ", exception)
    this.readingOrderItem.setDownloadStatus(
      PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloadFailed(
        readingOrderItem = this.readingOrderItem,
        exception = exception,
        message = exception.message ?: exception.javaClass.name
      )
    )
  }

  private fun onDownloading(
    progress: PlayerDownloadProgress
  ) {
    this.logger.debug("onDownloading: {}", progress)
    this.sharedState.progressSet(progress)
    this.readingOrderItem.setDownloadStatus(
      PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloading(
        readingOrderItem = this.readingOrderItem,
        progress = progress
      )
    )
  }

  private fun onNotDownloaded() {
    this.readingOrderItem.setDownloadStatus(
      PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemNotDownloaded(
        readingOrderItem = this.readingOrderItem
      )
    )
  }

  private fun onDownloaded() {
    this.readingOrderItem.setDownloadStatus(
      PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloaded(
        readingOrderItem = this.readingOrderItem
      )
    )
  }

  private fun onStartDownload(): CompletableFuture<Unit> {
    this.logger.debug("Download: {}", this.sharedState.bookDownloadURI)

    val request =
      PlayerDownloadRequest(
        uri = this.sharedState.bookDownloadURI,
        credentials = null,
        outputFile = this.sharedState.bookFile,
        outputFileTemp = this.sharedState.bookFileTemp,
        userAgent = this.sharedState.userAgent,
        onProgress = { percent ->
          this.onDownloading(PlayerDownloadProgress.percentClamp(percent))
        },
        onCompletion = {
          LCPDownloads.repackagePublication(
            source = this.sharedState.bookDownloadURI,
            licenseBytes = this.sharedState.licenseBytes,
            file = this.sharedState.bookFile,
            fileTemp = this.sharedState.bookFileTemp
          )
        }
      )

    val future = this.sharedState.downloadProvider.download(request)
    this.createDownloadingRequest(future)
    return future
  }

  private fun createDownloadingRequest(
    future: CompletableFuture<Unit>
  ) {
    this.sharedState.stateSet(State.Downloading(future))
    this.onBroadcastState()

    /*
     * Add a callback to the future that will report download success and failure.
     */

    future.whenComplete { unit, exception ->
      when (exception) {
        null -> {
          this@ExoDownloadWholeBookSingleFileTask.onDownloadCompleted()
        }

        is CancellationException ->
          this@ExoDownloadWholeBookSingleFileTask.onDownloadCancelled()

        else -> {
          this@ExoDownloadWholeBookSingleFileTask.onDownloadFailed(Exception(exception))
        }
      }
    }
  }

  private fun onDownloadCompleted() {
    this.logger.debug("onDownloadCompleted")
    this.sharedState.stateSet(State.Downloaded)
    this.onBroadcastState()
  }

  private fun onDownloadCancelled() {
    this.logger.error("onDownloadCancelled")
    this.sharedState.stateSet(State.Initial)
    this.onDeleteDownloaded()
  }
}
