package org.librarysimplified.audiobook.media3

import org.librarysimplified.audiobook.api.PlayerDownloadProgress
import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus
import org.librarysimplified.audiobook.api.PlayerDownloadTaskType
import org.librarysimplified.audiobook.api.PlayerDownloadWholeBookTaskType
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import java.net.URI

/**
 * An Exo implementation of the download-whole-book task.
 */

class ExoDownloadWholeBookTask(
  private val audioBook: ExoAudioBook
) : PlayerDownloadWholeBookTaskType {

  override fun fetch() {
    this.audioBook.downloadTasks.forEach(PlayerDownloadTaskType::fetch)
  }

  override fun cancel() {
    this.audioBook.downloadTasks.forEach(PlayerDownloadTaskType::cancel)
  }

  override fun delete() {
    this.audioBook.downloadTasks.forEach(PlayerDownloadTaskType::delete)
  }

  override val progress: PlayerDownloadProgress
    get() = this.calculateProgress()

  override val playbackURI: URI
    get() = URI.create("urn:unsupported")

  override val index: Int
    get() = 0

  override val status: PlayerDownloadTaskStatus
    get() = PlayerDownloadTaskStatus.IdleNotDownloaded

  override val readingOrderItems: List<PlayerReadingOrderItemType>
    get() = this.audioBook.readingOrder

  private fun calculateProgress(): PlayerDownloadProgress {
    val progressSum = this.audioBook.downloadTasks.sumOf { task -> task.progress.value }
    return PlayerDownloadProgress.normalClamp(progressSum / this.audioBook.downloadTasks.size)
  }
}
