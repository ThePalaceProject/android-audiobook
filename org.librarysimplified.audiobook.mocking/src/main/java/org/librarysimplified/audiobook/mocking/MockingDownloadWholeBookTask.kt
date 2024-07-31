package org.librarysimplified.audiobook.mocking

import org.librarysimplified.audiobook.api.PlayerDownloadProgress
import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus
import org.librarysimplified.audiobook.api.PlayerDownloadWholeBookTaskType
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import java.net.URI

/**
 * A fake download task.
 */

class MockingDownloadWholeBookTask(
  private val audioBook: MockingAudioBook,
  override val playbackURI: URI
) : PlayerDownloadWholeBookTaskType {

  override fun fetch() {
    this.audioBook.downloadTasks.forEach { task ->
      task.fetch()
    }
  }

  override fun cancel() {
    this.audioBook.downloadTasks.forEach { task ->
      task.cancel()
    }
  }

  override fun delete() {
    this.audioBook.downloadTasks.forEach { task ->
      task.delete()
    }
  }

  override val progress: PlayerDownloadProgress
    get() = calculateProgress()
  override val index: Int
    get() = 0
  override val status: PlayerDownloadTaskStatus
    get() = PlayerDownloadTaskStatus.IdleNotDownloaded

  override val readingOrderItems: List<PlayerReadingOrderItemType>
    get() = this.audioBook.readingOrder

  private fun calculateProgress(): PlayerDownloadProgress {
    val progressTotal =
      this.audioBook.downloadTasks.sumOf { task -> task.progress.value }
    return PlayerDownloadProgress.normalClamp(progressTotal / this.audioBook.downloadTasks.size)
  }
}
