package org.librarysimplified.audiobook.open_access

import org.librarysimplified.audiobook.api.PlayerDownloadWholeBookTaskType
import org.librarysimplified.audiobook.api.PlayerSpineElementType

/**
 * An Exo implementation of the download-whole-book task.
 */

class ExoDownloadWholeBookTask(
  private val audioBook: ExoAudioBook
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
    get() = audioBook.spine

  private fun calculateProgress(): Double {
    return this.audioBook.downloadTasks.sumOf { task -> task.progress } / this.audioBook.downloadTasks.size
  }
}
