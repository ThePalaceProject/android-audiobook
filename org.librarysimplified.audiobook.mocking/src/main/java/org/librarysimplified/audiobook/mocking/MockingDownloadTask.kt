package org.librarysimplified.audiobook.mocking

import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus
import org.librarysimplified.audiobook.api.PlayerDownloadTaskType
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloadFailed
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloading
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemNotDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import kotlin.random.Random

/**
 * A fake download task.
 */

class MockingDownloadTask(
  private val downloadStatusExecutor: ExecutorService,
  private val downloadProvider: PlayerDownloadProviderType,
  private val readingOrderItemList: List<MockingReadingOrderItem>,
  override val index: Int
) : PlayerDownloadTaskType {

  private val log = LoggerFactory.getLogger(MockingDownloadTask::class.java)

  private var percent: Int = 0
  private val stateLock: Any = Object()
  private var state: State = State.Initial

  init {
    this.onBroadcastState()
  }

  private sealed class State {
    data object Initial : State()
    data object Downloaded : State()
    data class Downloading(val future: CompletableFuture<Unit>) : State()
  }

  private fun stateGetCurrent() =
    synchronized(this.stateLock) { this.state }

  private fun stateSetCurrent(new_state: State) =
    synchronized(this.stateLock) { this.state = new_state }

  private fun onBroadcastState() {
    when (this.stateGetCurrent()) {
      State.Initial -> this.onNotDownloaded()
      State.Downloaded -> this.onDownloaded()
      is State.Downloading -> this.onDownloading(this.percent)
    }
  }

  private fun onNotDownloaded() {
    this.log.debug("not downloaded")
    this.readingOrderItemList.forEach { spineElement ->
      spineElement.setDownloadStatus(PlayerReadingOrderItemNotDownloaded(spineElement))
    }
  }

  private fun onDownloading(percent: Int) {
    this.percent = percent
    this.readingOrderItemList.forEach { spineElement ->
      spineElement.setDownloadStatus(PlayerReadingOrderItemDownloading(spineElement, percent))
    }
  }

  private fun onDownloaded() {
    this.log.debug("downloaded")
    this.readingOrderItemList.forEach { spineElement ->
      spineElement.setDownloadStatus(PlayerReadingOrderItemDownloaded(spineElement))
    }
  }

  private fun onStartDownload(): CompletableFuture<Unit> {
    this.log.debug("starting download")

    val future =
      this.downloadProvider.download(
        PlayerDownloadRequest(
          uri = URI.create("urn:" + Random.nextInt()),
          credentials = null,
          outputFile = File("/"),
          userAgent = PlayerUserAgent("org.librarysimplified.audiobook.mocking 1.0.0"),
          onProgress = { percent -> this.onDownloading(percent) }
        )
      )

    this.stateSetCurrent(State.Downloading(future))
    this.onBroadcastState()

    /*
     * Add a callback to the future that will report download success and failure.
     */

    future.whenComplete { unit, exception ->
      when (exception) {
        null -> {
          onDownloadCompleted()
        }
        is CancellationException ->
          onDownloadCancelled()
        else -> {
          onDownloadFailed(Exception(exception))
        }
      }
    }

    return future
  }

  private fun onDownloadCancelled() {
    this.log.error("onDownloadCancelled")
    this.stateSetCurrent(State.Initial)
    this.onBroadcastState()
    this.onDeleteDownloaded()
  }

  private fun onDownloadFailed(e: Exception) {
    this.log.error("onDownloadFailed: ", e)
    this.stateSetCurrent(State.Initial)
    this.onBroadcastState()
    this.readingOrderItemList.forEach { spineElement ->
      spineElement.setDownloadStatus(
        PlayerReadingOrderItemDownloadFailed(
          spineElement, e, e.message ?: "Missing exception message"
        )
      )
    }
  }

  private fun onDownloadCompleted() {
    this.log.debug("onDownloadCompleted")
    this.stateSetCurrent(State.Downloaded)
    this.onBroadcastState()
  }

  override fun fetch() {
    this.log.debug("fetch")

    when (this.stateGetCurrent()) {
      State.Initial -> this.onStartDownload()
      State.Downloaded -> this.onDownloaded()
      is State.Downloading -> this.onDownloading(this.percent)
    }
  }

  override fun delete() {
    this.log.debug("delete")

    val current = this.stateGetCurrent()
    when (current) {
      State.Initial -> this.onBroadcastState()
      State.Downloaded -> this.onDeleteDownloaded()
      is State.Downloading -> this.onDeleteDownloading(current)
    }
  }

  override fun cancel() {
    this.log.debug("cancel")

    val current = this.stateGetCurrent()
    when (current) {
      State.Initial -> this.onBroadcastState()
      State.Downloaded -> this.onBroadcastState()
      is State.Downloading -> this.onDeleteDownloading(current)
    }
  }

  private fun onDeleteDownloading(state: State.Downloading) {
    this.log.debug("cancelling download in progress")

    state.future.cancel(true)
    this.stateSetCurrent(State.Initial)
    this.onBroadcastState()
    this.onDeleteDownloaded()
  }

  private fun onDeleteDownloaded() {
    this.stateSetCurrent(State.Initial)
    this.onBroadcastState()
  }

  override val progress: Double
    get() = this.percent.toDouble()

  override val readingOrderItems: List<PlayerReadingOrderItemType>
    get() = this.readingOrderItemList

  override val status: PlayerDownloadTaskStatus
    get() = when (this.stateGetCurrent()) {
      State.Downloaded -> PlayerDownloadTaskStatus.IdleDownloaded
      is State.Downloading -> PlayerDownloadTaskStatus.Downloading(
        if (this.progress == 0.0) {
          null
        } else {
          this.progress
        }
      )
      State.Initial -> PlayerDownloadTaskStatus.IdleNotDownloaded
    }
}
