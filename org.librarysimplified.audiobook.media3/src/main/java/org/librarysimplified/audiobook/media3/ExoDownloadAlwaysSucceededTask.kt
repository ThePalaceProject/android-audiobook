package org.librarysimplified.audiobook.media3

import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus
import org.librarysimplified.audiobook.api.PlayerDownloadTaskType
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import java.net.URI

/**
 * A download task that pretends that it has always succeeded.
 */

class ExoDownloadAlwaysSucceededTask(
  override val index: Int,
  private val readingOrderItem: ExoReadingOrderItemHandle,
  override val playbackURI: URI,
) : PlayerDownloadTaskType {

  init {
    this.readingOrderItem.setDownloadStatus(PlayerReadingOrderItemDownloaded(this.readingOrderItem))
  }

  override val status: PlayerDownloadTaskStatus =
    PlayerDownloadTaskStatus.IdleDownloaded

  override fun fetch() {
    this.readingOrderItem.setDownloadStatus(PlayerReadingOrderItemDownloaded(this.readingOrderItem))
  }

  override fun cancel() {
    // Ignored.
  }

  override fun delete() {
    // Ignored.
  }

  override val progress: Double = 1.0

  override val readingOrderItems: List<PlayerReadingOrderItemType>
    get() = listOf(this.readingOrderItem)
}
