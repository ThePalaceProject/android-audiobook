package org.librarysimplified.audiobook.open_access

import org.librarysimplified.audiobook.api.PlayerDownloadWholeBookTaskType
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloaded
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloadFailed
import org.librarysimplified.audiobook.api.PlayerSpineElementType
import org.slf4j.LoggerFactory
import rx.Subscription

/**
 * An Exo implementation of the download-whole-book task.
 */

class ExoDownloadWholeBookTask(
  private val audioBook: ExoAudioBook
) : PlayerDownloadWholeBookTaskType {

  private val log = LoggerFactory.getLogger(ExoDownloadWholeBookTask::class.java)

  private var downloadEventsSubscription: Subscription? = null

  private var currentDownloadTaskIndex = 0
  private var currentFileName = ""

  init {
    this.downloadEventsSubscription =
      this.audioBook.spineElementDownloadStatus.subscribe(
        { event -> this.onDownloadStatusUpdated(event) },
        { error -> this.onDownloadError(error) }
      )
  }

  override fun fetch() {
    currentDownloadTaskIndex = 0
    fetchCurrentDownloadTask()
  }

  override fun cancel() {
    this.audioBook.downloadTasks.forEach { task ->
      if (task.spineItems.filterIsInstance<ExoSpineElement>().any { item ->
          item.downloadStatus !is PlayerSpineElementDownloaded
        }) {
          task.cancel()
        }
      }

    downloadEventsSubscription?.unsubscribe()
  }

  override fun delete() {
    this.audioBook.downloadTasks.forEach { task ->
      task.delete()
    }

    downloadEventsSubscription?.unsubscribe()
  }

  private fun fetchCurrentDownloadTask() {
    if (currentDownloadTaskIndex >= this.audioBook.downloadTasks.size) {
      return
    }

    val task = this.audioBook.downloadTasks[currentDownloadTaskIndex]
    currentFileName = task.partFile.name

    if (!task.spineItems.all { item -> item.downloadStatus is PlayerSpineElementDownloaded }) {
      task.fetch()
    } else {
      currentDownloadTaskIndex++
      fetchCurrentDownloadTask()
    }
  }

  private fun onDownloadError(error: Throwable) {
    this.log.error("onDownloadError: error: ", error)
    return
  }

  private fun onDownloadStatusUpdated(status: PlayerSpineElementDownloadStatus) {
    val element = status.spineElement
    val downloadTask = this.audioBook.downloadTasks.firstOrNull { task ->
      task.fulfillsSpineElement(element)
    }

    if (downloadTask == null) {
      this.log.error("[{}]: onDownloadStatusUpdated: no download task available", element.id)
      return
    }

    when (status) {
      is PlayerSpineElementDownloaded -> {
        this.log.debug("[{}]: onDownloadStatusUpdated: completed downloading task", element.id)
        startNextTaskIfOnSequence(downloadTask.partFile.name)
      }
      is PlayerSpineElementDownloadFailed -> {
        this.log.error("[{}]: onDownloadStatusUpdated: failed downloading task", element.id)
        startNextTaskIfOnSequence(downloadTask.partFile.name)
      }
      else -> {
        this.log.debug("[{}]: onDownloadStatusUpdated: {}", element.id, status)
      }
    }
  }

  private fun startNextTaskIfOnSequence(fileName: String) {

    // the user can download a chapter ahead of the current order, so we need to check if
    // we should move on with the next task in the sequence or not. If the file name is equal
    // to the file name set at the beginning of the task, it means the download started from
    // the "whole book task" sequence and we can advance. Otherwise, it means the task that
    // was completed was started by the user and we can't increment the variable and start a
    // new task because it would cause a weird behavior on the downloading tasks and UI
    if (currentFileName == fileName) {
      currentDownloadTaskIndex++
      fetchCurrentDownloadTask()
    }
  }

  override fun fulfillsSpineElement(spineElement: PlayerSpineElementType): Boolean {
    return spineItems.contains(spineElement)
  }

  override val progress: Double
    get() = calculateProgress()

  override val spineItems: List<PlayerSpineElementType>
    get() = audioBook.spine

  private fun calculateProgress(): Double {
    return this.audioBook.downloadTasks.sumOf { task -> task.progress } / this.audioBook.downloadTasks.size
  }
}
