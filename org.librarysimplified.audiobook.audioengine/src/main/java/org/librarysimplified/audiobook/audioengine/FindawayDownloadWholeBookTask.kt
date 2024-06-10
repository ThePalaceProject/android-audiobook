package org.librarysimplified.audiobook.audioengine

import io.audioengine.mobile.DownloadEvent
import io.audioengine.mobile.DownloadRequest
import io.reactivex.disposables.Disposable
import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus
import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus.Downloading
import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus.IdleDownloaded
import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus.IdleNotDownloaded
import org.librarysimplified.audiobook.api.PlayerDownloadTaskType
import org.librarysimplified.audiobook.api.PlayerDownloadWholeBookTaskType
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloadFailed
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloading
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemNotDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * A download task capable of fetching an entire book.
 */

class FindawayDownloadWholeBookTask(
  private val audioBook: FindawayAudioBook,
  private val downloadEngine: FindawayDownloadEngineType,
  override val playbackURI: URI
) : PlayerDownloadWholeBookTaskType {

  private val log =
    LoggerFactory.getLogger(FindawayDownloadWholeBookTask::class.java)

  private var downloadEventsSubscription: Disposable? = null

  private val downloadRequest =
    DownloadRequest(
      contentId = this.audioBook.findawayManifest.fulfillmentId,
      licenseId = this.audioBook.findawayManifest.licenseId,
      chapter = this.audioBook.readingOrder[0].itemManifest.sequence,
      part = this.audioBook.readingOrder[0].itemManifest.part,
      type = DownloadRequest.Type.TO_END
    )

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
    get() = IdleNotDownloaded

  override val readingOrderItems: List<PlayerReadingOrderItemType>
    get() = this.audioBook.readingOrder

  private fun determineProgress(): Double {
    return this.audioBook.downloadTasks.sumOf { task ->
      task.progress
    } / this.audioBook.downloadTasks.size
  }

  override fun cancel() {
    this.log.debug("cancel")

    /*
     * The Findaway player will frequently just lose requests. We maximize the chances of any
     * request actually being processed by submitting it multiple times, and then trying again
     * a second or so later.
     */

    for (i in 0 until 3) {
      val delay = i * 250L
      PlayerUIThread.runOnUIThreadDelayed({
        this.downloadEngine.pause(this.downloadRequest)
        this.setAllCancelled()
      }, delay)
    }
  }

  private fun setAllCancelled() {
    this.audioBook.downloadTasks.forEach { task: PlayerDownloadTaskType ->
      if (task is FindawayChapterDownloadTask) {
        when (val s = task.status) {
          is Downloading -> {
            task.setStatus(IdleNotDownloaded)
          }

          is PlayerDownloadTaskStatus.Failed -> {
            task.setStatus(s)
          }

          IdleDownloaded -> {
            task.setStatus(IdleDownloaded)
          }

          IdleNotDownloaded -> {
            task.setStatus(IdleNotDownloaded)
          }
        }
      } else {
        throw IllegalStateException("Task is of an unexpected type.")
      }
    }
  }

  override fun delete() {
    this.log.debug("delete")

    /*
     * The Findaway player will frequently just lose requests. We maximize the chances of any
     * request actually being processed by submitting it multiple times, and then trying again
     * a second or so later.
     */

    for (i in 0 until 3) {
      val delay = i * 250L
      PlayerUIThread.runOnUIThreadDelayed({
        this.downloadEngine.delete(this.downloadRequest)
        this.setAllDeleted()
      }, delay)
    }
  }

  private fun setAllDeleted() {
    this.audioBook.downloadTasks.forEach { task: PlayerDownloadTaskType ->
      if (task is FindawayChapterDownloadTask) {
        task.setStatus(IdleNotDownloaded)
      } else {
        throw IllegalStateException("Task is of an unexpected type.")
      }
    }
  }

  override fun fetch() {
    this.log.debug("fetch")
    this.downloadEngine.download(this.downloadRequest)
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
        downloadTaskWithElement.setStatus(
          PlayerDownloadTaskStatus.Failed(
            message = event.message ?: "Download failed.",
            exception = null
          )
        )
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
          downloadTaskWithElement.setStatus(IdleDownloaded)
          downloadTaskWithElement.readingOrderItems.forEach { spineElement ->
            val findawayElement = spineElement as FindawayReadingOrderItem
            findawayElement.setDownloadStatus(PlayerReadingOrderItemDownloaded(findawayElement))
          }
          return
        }

        DownloadEvent.DOWNLOAD_PAUSED,
        DownloadEvent.DOWNLOAD_CANCELLED -> {
          downloadTaskWithElement.setStatus(IdleNotDownloaded)
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
          downloadTaskWithElement.setStatus(Downloading(0.0))
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

          downloadTaskWithElement.setStatus(Downloading(percentage.toDouble()))
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
