package org.librarysimplified.audiobook.audioengine

import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus
import org.librarysimplified.audiobook.api.PlayerDownloadTaskType
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloadExpired
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloadFailed
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloading
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemNotDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import java.net.URI

class FindawayChapterDownloadTask(
  override val index: Int,
  override val playbackURI: URI,
  private val readingOrderItem: FindawayReadingOrderItem
) : PlayerDownloadTaskType {

  override fun fetch() {
    // Nothing
  }

  override fun cancel() {
    // Nothing
  }

  override fun delete() {
    // Nothing
  }

  fun setStatus(status: PlayerDownloadTaskStatus) {
    this.readingOrderItem.setDownloadStatus(
      when (status) {
        is PlayerDownloadTaskStatus.Downloading ->
          PlayerReadingOrderItemDownloading(
            readingOrderItem = this.readingOrderItem,
            percent = (status.progress ?: 0).toInt()
          )

        is PlayerDownloadTaskStatus.Failed ->
          PlayerReadingOrderItemDownloadFailed(
            readingOrderItem = this.readingOrderItem,
            exception = status.exception,
            message = status.message
          )

        PlayerDownloadTaskStatus.IdleDownloaded ->
          PlayerReadingOrderItemDownloaded(this.readingOrderItem)

        PlayerDownloadTaskStatus.IdleNotDownloaded ->
          PlayerReadingOrderItemNotDownloaded(this.readingOrderItem)
      }
    )
  }

  override val status: PlayerDownloadTaskStatus
    get() = when (val s = this.readingOrderItem.downloadStatus) {
      is PlayerReadingOrderItemDownloadExpired ->
        PlayerDownloadTaskStatus.IdleNotDownloaded

      is PlayerReadingOrderItemDownloadFailed ->
        PlayerDownloadTaskStatus.Failed(s.message, s.exception)

      is PlayerReadingOrderItemDownloaded ->
        PlayerDownloadTaskStatus.IdleDownloaded

      is PlayerReadingOrderItemDownloading ->
        PlayerDownloadTaskStatus.Downloading(s.percent.toDouble())

      is PlayerReadingOrderItemNotDownloaded ->
        PlayerDownloadTaskStatus.IdleNotDownloaded
    }

  override val progress: Double
    get() = when (val s = this.status) {
      is PlayerDownloadTaskStatus.Downloading -> s.progress ?: 0.0
      is PlayerDownloadTaskStatus.Failed -> 0.0
      PlayerDownloadTaskStatus.IdleDownloaded -> 100.0
      PlayerDownloadTaskStatus.IdleNotDownloaded -> 0.0
    }

  override val readingOrderItems: List<PlayerReadingOrderItemType>
    get() = listOf(this.readingOrderItem)
}
