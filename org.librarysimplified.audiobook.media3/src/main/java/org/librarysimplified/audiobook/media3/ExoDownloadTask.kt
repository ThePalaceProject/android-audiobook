package org.librarysimplified.audiobook.media3

import net.jcip.annotations.GuardedBy
import org.librarysimplified.audiobook.api.PlayerDownloadProgress
import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus
import org.librarysimplified.audiobook.api.PlayerDownloadTaskType
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloadExpired
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloadFailed
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloading
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemNotDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.api.extensions.PlayerExtensionType
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.audiobook.media3.ExoDownloadTask.State.Downloaded
import org.librarysimplified.audiobook.media3.ExoDownloadTask.State.Downloading
import org.librarysimplified.audiobook.media3.ExoDownloadTask.State.Failed
import org.librarysimplified.audiobook.media3.ExoDownloadTask.State.Initial
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

/**
 * An Exo implementation of the download task.
 */

class ExoDownloadTask(
  private val downloadStatusExecutor: ExecutorService,
  private val downloadProvider: PlayerDownloadProviderType,
  private val originalLink: PlayerManifestLink,
  private val readingOrderItem: ExoReadingOrderItemHandle,
  private val userAgent: PlayerUserAgent,
  private val extensions: List<PlayerExtensionType>,
  val partFile: File,
  val partFileTemp: File,
  override val index: Int
) : PlayerDownloadTaskType {

  private val log =
    LoggerFactory.getLogger(ExoDownloadTask::class.java)

  private var progressValue: PlayerDownloadProgress = PlayerDownloadProgress(0.0)
  private val stateLock: Any = Object()

  @GuardedBy("stateLock")
  private var state: State =
    when (this.partFile.isFile) {
      true -> Downloaded
      false -> Initial
    }

  init {
    this.onBroadcastState()
  }

  private sealed class State {
    data object Initial : State()
    data class Failed(
      val message: String,
      val exception: Exception
    ) : State()

    data object Downloaded : State()
    data class Downloading(val future: CompletableFuture<Unit>) : State()
  }

  private fun stateGetCurrent() =
    synchronized(this.stateLock) { this.state }

  private fun stateSetCurrent(newState: State) =
    synchronized(this.stateLock) { this.state = newState }

  private fun onBroadcastState() {
    when (val s = this.stateGetCurrent()) {
      Initial -> this.onNotDownloaded()
      Downloaded -> this.onDownloaded()
      is Downloading -> this.onDownloading(this.progressValue)
      is Failed -> this.onDownloadFailed(s.exception)
    }
  }

  private fun onNotDownloaded() {
    this.log.debug("[{}] not downloaded", this.readingOrderItem.id)
    this.readingOrderItem.setDownloadStatus(
      PlayerReadingOrderItemNotDownloaded(this.readingOrderItem)
    )
  }

  private fun onDownloading(
    progress: PlayerDownloadProgress
  ) {
    this.log.debug("[{}] onDownloading {}", this.readingOrderItem.id, progress)

    this.progressValue = progress
    this.readingOrderItem.setDownloadStatus(
      PlayerReadingOrderItemDownloading(this.readingOrderItem, progress)
    )
  }

  private fun onDownloaded() {
    this.log.debug("[{}] onDownloaded {}", this.readingOrderItem.id, this.progress)
    this.readingOrderItem.setDownloadStatus(PlayerReadingOrderItemDownloaded(this.readingOrderItem))
  }

  private fun createDownloadingRequest(
    future: CompletableFuture<Unit>,
    targetLink: PlayerManifestLink
  ) {
    this.stateSetCurrent(Downloading(future))
    this.onBroadcastState()

    /*
     * Add a callback to the future that will report download success and failure.
     */

    future.whenComplete { unit, exception ->
      when (exception) {
        null -> {
          this@ExoDownloadTask.onDownloadCompleted()
        }

        is CancellationException ->
          this@ExoDownloadTask.onDownloadCancelled()

        else -> {
          if (targetLink.expires) {
            this@ExoDownloadTask.onDownloadExpired(Exception(exception))
          } else {
            this@ExoDownloadTask.onDownloadFailed(Exception(exception))
          }
        }
      }
    }
  }

  private fun onStartDownload(): CompletableFuture<Unit> {
    this.log.debug("[{}] download: {}", this.readingOrderItem.id, this.originalLink.hrefURI)

    val request =
      PlayerDownloadRequest(
        uri = this.originalLink.hrefURI ?: URI.create("urn:missing"),
        credentials = null,
        outputFile = this.partFile,
        outputFileTemp = this.partFileTemp,
        userAgent = this.userAgent,
        onProgress = { percent ->
          this.onDownloading(PlayerDownloadProgress.percentClamp(percent))
        },
        onCompletion = { }
      )

    val future =
      this.onStartDownloadForRequest(request, this.originalLink)

    this.createDownloadingRequest(future, this.originalLink)
    return future
  }

  private fun onStartDownloadForRequest(
    request: PlayerDownloadRequest,
    targetLink: PlayerManifestLink
  ): CompletableFuture<Unit> {
    for (extension in this.extensions) {
      val future =
        extension.onDownloadLink(
          statusExecutor = this.downloadStatusExecutor,
          downloadProvider = this.downloadProvider,
          originalRequest = request,
          link = targetLink
        )
      if (future != null) {
        this.log.debug("extension {} provided a download substitution", extension.name)
        return future
      }
    }

    return this.downloadProvider.download(request)
  }

  private fun onDownloadCancelled() {
    this.log.error("[{}] onDownloadCancelled", this.readingOrderItem.id)
    this.stateSetCurrent(Initial)
    this.onDeleteDownloaded()
  }

  private fun onDownloadExpired(exception: Exception) {
    this.log.error("[{}] onDownloadExpired: ", this.readingOrderItem.id, exception)
    this.stateSetCurrent(Initial)
    this.readingOrderItem.setDownloadStatus(
      PlayerReadingOrderItemDownloadExpired(
        this.readingOrderItem, exception, exception.message ?: "Missing exception message"
      )
    )
  }

  private fun onDownloadFailed(exception: Exception) {
    this.log.error("[{}] onDownloadFailed: ", this.readingOrderItem.id, exception)
    this.stateSetCurrent(Failed(exceptionMessage(exception), exception))
    this.readingOrderItem.setDownloadStatus(
      PlayerReadingOrderItemDownloadFailed(
        this.readingOrderItem, exception, exception.message ?: "Missing exception message"
      )
    )
  }

  private fun exceptionMessage(exception: Exception): String {
    return exception.message ?: return exception.javaClass.simpleName
  }

  private fun onDownloadCompleted() {
    this.log.debug("[{}] onDownloadCompleted", this.readingOrderItem.id)
    this.stateSetCurrent(Downloaded)
    this.onBroadcastState()
  }

  private fun onDeleteDownloading(state: Downloading) {
    this.log.debug("[{}] onDeleteDownloading", this.readingOrderItem.id)

    state.future.cancel(true)
    this.stateSetCurrent(Initial)
    this.onDeleteDownloaded()
  }

  private fun onDeleteDownloaded() {
    this.log.debug("[{}] onDeleteDownloaded {}", this.readingOrderItem.id, this.partFile)

    try {
      ExoFileIO.fileDelete(this.partFile)
    } catch (e: Exception) {
      this.log.error("[{}] failed to delete file: ", this.readingOrderItem.id, e)
    } finally {
      this.stateSetCurrent(Initial)
      this.onBroadcastState()
    }
  }

  override fun delete() {
    this.log.debug("[{}] delete", this.readingOrderItem.id)

    return when (val current = this.stateGetCurrent()) {
      Initial -> this.onBroadcastState()
      Downloaded -> this.onDeleteDownloaded()
      is Downloading -> this.onDeleteDownloading(current)
      is Failed -> this.onBroadcastState()
    }
  }

  override val playbackURI: URI
    get() = when (this.stateGetCurrent()) {
      Downloaded -> this.partFile.toURI()
      is Downloading -> this.originalLink.hrefURI!!
      is Failed -> this.originalLink.hrefURI!!
      Initial -> this.originalLink.hrefURI!!
    }

  override val status: PlayerDownloadTaskStatus
    get() = when (val s = this.stateGetCurrent()) {
      Downloaded -> PlayerDownloadTaskStatus.IdleDownloaded
      is Downloading -> PlayerDownloadTaskStatus.Downloading(
        if (this.progress.value == 0.0) {
          null
        } else {
          this.progress
        }
      )

      Initial -> PlayerDownloadTaskStatus.IdleNotDownloaded
      is Failed -> PlayerDownloadTaskStatus.Failed(s.message, s.exception)
    }

  override fun fetch() {
    this.log.debug("[{}] fetch", this.readingOrderItem.id)

    when (this.stateGetCurrent()) {
      Initial -> {
        this.onStartDownload()
      }

      Downloaded -> {
        this.onDownloaded()
      }

      is Downloading -> {
        this.onDownloading(this.progress)
      }

      is Failed -> {
        this.onStartDownload()
      }
    }
  }

  override fun cancel() {
    this.log.debug("cancel")

    return when (val current = this.stateGetCurrent()) {
      Initial -> this.onBroadcastState()
      Downloaded -> this.onBroadcastState()
      is Downloading -> this.onDeleteDownloading(current)
      is Failed -> this.onBroadcastState()
    }
  }

  override val progress: PlayerDownloadProgress
    get() = this.progressValue

  override val readingOrderItems: List<PlayerReadingOrderItemType>
    get() = listOf(this.readingOrderItem)
}
