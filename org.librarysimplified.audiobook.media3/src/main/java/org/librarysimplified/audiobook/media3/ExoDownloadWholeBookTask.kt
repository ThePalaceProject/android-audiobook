package org.librarysimplified.audiobook.media3

import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus
import org.librarysimplified.audiobook.api.PlayerDownloadWholeBookTaskType
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType

/**
 * An Exo implementation of the download-whole-book task.
 */

class ExoDownloadWholeBookTask(
  private val audioBook: ExoAudioBook
) : PlayerDownloadWholeBookTaskType {

  override fun fetch() {
    this.audioBook.downloadTasks.forEach(ExoDownloadTask::fetch)
  }

  override fun cancel() {
    this.audioBook.downloadTasks.forEach(ExoDownloadTask::cancel)
  }

  override fun delete() {
    this.audioBook.downloadTasks.forEach(ExoDownloadTask::delete)
  }

  override val progress: Double
    get() = this.calculateProgress()
  override val index: Int
    get() = 0

  override val status: PlayerDownloadTaskStatus
    get() = PlayerDownloadTaskStatus.IdleNotDownloaded

  override val readingOrderItems: List<PlayerReadingOrderItemType>
    get() = this.audioBook.readingOrder

  private fun calculateProgress(): Double {
    return this.audioBook.downloadTasks.sumOf { task -> task.progress } / this.audioBook.downloadTasks.size
  }
}
