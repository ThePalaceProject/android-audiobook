package org.librarysimplified.audiobook.mocking

import org.librarysimplified.audiobook.api.PlayerDownloadWholeBookTaskType
import org.librarysimplified.audiobook.api.PlayerSpineElementType

/**
 * A fake download task.
 */

class MockingDownloadWholeBookTask(
  private val audioBook: MockingAudioBook
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

  override val progress: Double
    get() = calculateProgress()

  override val spineItems: List<PlayerSpineElementType>
    get() = this.audioBook.spineItems

  private fun calculateProgress(): Double {
    return this.audioBook.downloadTasks.sumOf { task -> task.progress } / this.audioBook.downloadTasks.size
  }
}
