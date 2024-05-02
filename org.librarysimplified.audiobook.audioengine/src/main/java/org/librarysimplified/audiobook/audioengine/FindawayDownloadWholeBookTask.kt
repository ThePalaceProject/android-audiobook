package org.librarysimplified.audiobook.audioengine

import io.audioengine.mobile.DownloadEvent
import io.reactivex.disposables.Disposable
import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus
import org.librarysimplified.audiobook.api.PlayerDownloadWholeBookTaskType
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloadFailed
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloading
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemNotDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.slf4j.LoggerFactory

/**
 * A download task capable of fetching an entire book.
 */

class FindawayDownloadWholeBookTask(
  private val audioBook: FindawayAudioBook,
  private val downloadEngine: FindawayDownloadEngineType
) : PlayerDownloadWholeBookTaskType {

  private val log =
    LoggerFactory.getLogger(FindawayDownloadWholeBookTask::class.java)

  private var downloadEventsSubscription: Disposable? = null

  private var currentDownloadTaskIndex = 0
  private var currentChapter = 0
  private var currentPart = 0

  init {
    this.downloadEventsSubscription =
      this.downloadEngine.events.subscribe(
        { event -> this.onDownloadEvent(event) },
        { error -> this.onDownloadError(error) })
  }

  override val progress: Double
    get() = this.determineProgress()

  override val index: Int
    get() = 0

  override val status: PlayerDownloadTaskStatus
    get() = run {
      val task = this.audioBook.downloadTasks[this.currentDownloadTaskIndex]
        as FindawayChapterDownloadTask
      return task.status
    }

  override val readingOrderItems: List<PlayerReadingOrderItemType>
    get() = this.audioBook.readingOrder

  private fun determineProgress(): Double {
    return this.audioBook.downloadTasks.sumOf { task ->
      task.progress
    } / this.audioBook.downloadTasks.size
  }

  override fun cancel() {
    this.log.debug("cancel")

    this.audioBook.downloadTasks.forEach { task ->
      if (task is FindawayChapterDownloadTask) {
        if (task.readingOrderItems.filterIsInstance<FindawayReadingOrderItem>().any { item ->
            item.downloadStatus !is PlayerReadingOrderItemDownloaded
          }) {
          task.setProgress(0.0)
          task.cancel()
        }
      }
    }

    this.downloadEventsSubscription?.dispose()
  }

  override fun delete() {
    this.log.debug("delete")

    this.audioBook.downloadTasks.forEach { task ->
      (task as FindawayChapterDownloadTask).setProgress(0.0)
      task.readingOrderItems.forEach { item ->
        (item as FindawayReadingOrderItem).setDownloadStatus(
          PlayerReadingOrderItemNotDownloaded(item)
        )
      }
      task.delete()
    }

    this.downloadEventsSubscription?.dispose()
  }

  override fun fetch() {
    this.log.debug("fetch")

    this.currentDownloadTaskIndex = 0
    this.fetchCurrentDownloadTask()
  }

  private fun fetchCurrentDownloadTask() {
    if (this.currentDownloadTaskIndex >= this.audioBook.downloadTasks.size) {
      return
    }

    this.log.debug("fetch task number {}", this.currentDownloadTaskIndex)

    val task = this.audioBook.downloadTasks[this.currentDownloadTaskIndex] as FindawayChapterDownloadTask
    this.currentChapter = task.chapter
    this.currentPart = task.part

    if (!task.readingOrderItems.all { item -> item.downloadStatus is PlayerReadingOrderItemDownloaded }) {
      task.setProgress(0.0)
      task.fetch()
    } else {
      this.currentDownloadTaskIndex++
      this.fetchCurrentDownloadTask()
    }
  }

  private fun onDownloadError(error: Throwable) {
    this.log.error("onDownloadError: error: ", error)
    return
  }

  private fun onDownloadEvent(
    event: DownloadEvent
  ) {
    val chapter = event.chapter
    if (chapter != null) {
      val part = chapter.part
      val chap = chapter.chapter
      this.log.trace("[{} {}]: onDownloadEvent: {}", part, chap, event.toString())

      val element: FindawayReadingOrderItem? =
        this.audioBook.readingOrderForPartAndSequence(part, chap)

      if (element == null) {
        this.log.error("[{} {}]: onDownloadEvent: no spine element available", part, chap)
        return
      }

      val downloadTaskWithElement =
        this.audioBook.downloadTasks.firstOrNull { task ->
          task.readingOrderItems.contains(element)
        }

      if (downloadTaskWithElement == null || downloadTaskWithElement !is FindawayChapterDownloadTask) {
        this.log.error("[{} {}]: onDownloadEvent: no download task available", part, chap)
        return
      }

      if (event.isError) {
        downloadTaskWithElement.setProgress(0.0)
        downloadTaskWithElement.readingOrderItems.forEach { spineElement ->
          val findawayElement = spineElement as FindawayReadingOrderItem
          findawayElement.setDownloadStatus(
            PlayerReadingOrderItemDownloadFailed(findawayElement, event, "Download failed")
          )
        }
        return
      }

      when (event.code) {
        DownloadEvent.CHAPTER_ALREADY_DOWNLOADED,
        DownloadEvent.CHAPTER_DOWNLOAD_COMPLETED -> {
          downloadTaskWithElement.setProgress(100.0)
          downloadTaskWithElement.readingOrderItems.forEach { spineElement ->
            val findawayElement = spineElement as FindawayReadingOrderItem
            findawayElement.setDownloadStatus(PlayerReadingOrderItemDownloaded(findawayElement))
          }
          return
        }

        DownloadEvent.DOWNLOAD_PAUSED,
        DownloadEvent.DOWNLOAD_CANCELLED -> {
          downloadTaskWithElement.setProgress(0.0)
          downloadTaskWithElement.readingOrderItems.forEach { spineElement ->
            val findawayElement = spineElement as FindawayReadingOrderItem
            findawayElement.setDownloadStatus(
              PlayerReadingOrderItemNotDownloaded(
                findawayElement
              )
            )
          }
          return
        }

        DownloadEvent.DOWNLOAD_STARTED -> {
          downloadTaskWithElement.setProgress(0.0)
          downloadTaskWithElement.readingOrderItems.forEach { spineElement ->
            val findawayElement = spineElement as FindawayReadingOrderItem
            findawayElement.setDownloadStatus(
              PlayerReadingOrderItemDownloading(
                findawayElement,
                0
              )
            )
          }
          return
        }

        DownloadEvent.DOWNLOAD_PROGRESS_UPDATE -> {
          val percentage = event.chapterPercentage
          this.log.trace(
            "[{} {}]: onDownloadEvent: progress update {} ", part, chap, percentage
          )

          if (percentage >= 100 || downloadTaskWithElement.readingOrderItems.all { taskElement ->
              taskElement.downloadStatus is PlayerReadingOrderItemDownloaded
            }) {
            this.log.trace(
              "[{} {}]: onDownloadEvent: ignoring irrelevant progress update", part, chap
            )
            return
          }

          downloadTaskWithElement.setProgress(percentage.toDouble())
          downloadTaskWithElement.readingOrderItems.forEach { spineElement ->
            val findawayElement = spineElement as FindawayReadingOrderItem
            findawayElement.setDownloadStatus(
              PlayerReadingOrderItemDownloading(
                findawayElement,
                percentage
              )
            )
          }

          return
        }
      }
    } else {
      this.log.trace("[no chapter]: onDownloadEvent: {}", event.toString())
    }

    if (event.isError) {
      this.log.error("[no chapter]: onDownloadEvent: error received: ", event)
      return
    }
  }
}
