package org.librarysimplified.audiobook.open_access

import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import net.jcip.annotations.GuardedBy
import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
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
import org.librarysimplified.audiobook.open_access.ExoDownloadTask.State.Downloaded
import org.librarysimplified.audiobook.open_access.ExoDownloadTask.State.Downloading
import org.librarysimplified.audiobook.open_access.ExoDownloadTask.State.Initial
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService

/**
 * An Exo implementation of the download task.
 */

class ExoDownloadTask(
  private val downloadStatusExecutor: ExecutorService,
  private val downloadProvider: PlayerDownloadProviderType,
  private val originalLink: PlayerManifestLink,
  override val readingOrderItems: List<ExoReadingOrderItemHandle>,
  private val userAgent: PlayerUserAgent,
  private val extensions: List<PlayerExtensionType>,
  val partFile: File
) : PlayerDownloadTaskType {

  private val log = LoggerFactory.getLogger(ExoDownloadTask::class.java)
  private var percent: Int = 0
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
    object Initial : State()
    object Downloaded : State()
    data class Downloading(val future: ListenableFuture<Unit>) : State()
  }

  private fun stateGetCurrent() =
    synchronized(this.stateLock) { this.state }

  private fun stateSetCurrent(new_state: State) =
    synchronized(this.stateLock) { this.state = new_state }

  private fun onBroadcastState() {
    when (this.stateGetCurrent()) {
      Initial -> this.onNotDownloaded()
      Downloaded -> this.onDownloaded()
      is Downloading -> this.onDownloading(this.percent)
    }
  }

  private fun onNotDownloaded() {
    this.log.debug("not downloaded")
    this.readingOrderItems.forEach { spineElement ->
      spineElement.setDownloadStatus(PlayerReadingOrderItemNotDownloaded(spineElement))
    }
  }

  private fun onDownloading(percent: Int) {
    this.percent = percent
    this.readingOrderItems.forEach { spineElement ->
      spineElement.setDownloadStatus(PlayerReadingOrderItemDownloading(spineElement, percent))
    }
  }

  private fun onDownloaded() {
    this.log.debug("downloaded")
    this.readingOrderItems.forEach { spineElement ->
      spineElement.setDownloadStatus(PlayerReadingOrderItemDownloaded(spineElement))
    }
  }

  private fun createDownloadingRequest(
    future: ListenableFuture<Unit>,
    targetLink: PlayerManifestLink
  ) {
    this.stateSetCurrent(Downloading(future))
    this.onBroadcastState()

    /*
     * Add a callback to the future that will report download success and failure.
     */

    Futures.addCallback(
      future,
      object : FutureCallback<Unit> {
        override fun onSuccess(result: Unit?) {
          this@ExoDownloadTask.onDownloadCompleted()
        }

        override fun onFailure(exception: Throwable) {
          when (exception) {
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
      },
      this.downloadStatusExecutor
    )
  }

  private fun onStartDownload(): ListenableFuture<Unit> {
    this.log.debug("download: {}", this.originalLink.hrefURI)

    val request =
      PlayerDownloadRequest(
        uri = this.originalLink.hrefURI ?: URI.create("urn:missing"),
        credentials = null,
        outputFile = this.partFile,
        userAgent = this.userAgent,
        onProgress = { percent -> this.onDownloading(percent) }
      )

    val future =
      this.onStartDownloadForRequest(request, this.originalLink)

    this.createDownloadingRequest(future, this.originalLink)

    return future
  }

  private fun onStartDownloadForRequest(
    request: PlayerDownloadRequest,
    targetLink: PlayerManifestLink
  ): ListenableFuture<Unit> {
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
    this.log.error("onDownloadCancelled")
    this.stateSetCurrent(Initial)
    this.onDeleteDownloaded()
  }

  private fun onDownloadExpired(exception: Exception) {
    this.log.error("onDownloadExpired: ", exception)
    this.stateSetCurrent(Initial)
    this.readingOrderItems.forEach { item ->
      item.setDownloadStatus(
        PlayerReadingOrderItemDownloadExpired(
          item, exception, exception.message ?: "Missing exception message"
        )
      )
    }
  }

  private fun onDownloadFailed(exception: Exception) {
    this.log.error("onDownloadFailed: ", exception)
    this.stateSetCurrent(Initial)
    this.readingOrderItems.forEach { item ->
      item.setDownloadStatus(
        PlayerReadingOrderItemDownloadFailed(
          item, exception, exception.message ?: "Missing exception message"
        )
      )
    }
  }

  private fun onDownloadCompleted() {
    this.log.debug("onDownloadCompleted")
    this.stateSetCurrent(Downloaded)
    this.onBroadcastState()
  }

  private fun onDeleteDownloading(state: Downloading) {
    this.log.debug("cancelling download in progress")

    state.future.cancel(true)
    this.stateSetCurrent(Initial)
    this.onDeleteDownloaded()
  }

  private fun onDeleteDownloaded() {
    this.log.debug("deleting file {}", this.partFile)

    try {
      ExoFileIO.fileDelete(this.partFile)
    } catch (e: Exception) {
      this.log.error("failed to delete file: ", e)
    } finally {
      this.stateSetCurrent(Initial)
      this.onBroadcastState()
    }
  }

  override fun delete() {
    this.log.debug("delete")

    return when (val current = this.stateGetCurrent()) {
      Initial -> this.onBroadcastState()
      Downloaded -> this.onDeleteDownloaded()
      is Downloading -> this.onDeleteDownloading(current)
    }
  }

  override fun fetch() {
    this.log.debug("fetch")

    when (this.stateGetCurrent()) {
      Initial -> {
        this.onStartDownload()
      }
      Downloaded -> {
        this.onDownloaded()
      }
      is Downloading -> {
        this.onDownloading(this.percent)
      }
    }
  }

  override fun cancel() {
    this.log.debug("cancel")

    return when (val current = this.stateGetCurrent()) {
      Initial -> this.onBroadcastState()
      Downloaded -> this.onBroadcastState()
      is Downloading -> this.onDeleteDownloading(current)
    }
  }

  override val progress: Double
    get() = this.percent.toDouble()
}
