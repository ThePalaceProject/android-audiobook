package org.librarysimplified.audiobook.audioengine

import io.audioengine.mobile.DownloadRequest
import org.librarysimplified.audiobook.api.PlayerDownloadTaskStatus
import org.librarysimplified.audiobook.api.PlayerDownloadTaskType
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType

/**
 * A class for performing downloads for a single chapter.
 */

class FindawayChapterDownloadTask(
  private val downloadEngine: FindawayDownloadEngineType,
  private val manifest: FindawayManifest,
  private val spineElements: List<FindawayReadingOrderItem>,
  val chapter: Int,
  val part: Int,
  override val index: Int
) : PlayerDownloadTaskType {

  @Volatile
  private var stateField: PlayerDownloadTaskStatus =
    PlayerDownloadTaskStatus.IdleNotDownloaded

  private val request: DownloadRequest =
    DownloadRequest(
      chapter = this.chapter,
      contentId = this.manifest.fulfillmentId,
      licenseId = this.manifest.licenseId,
      part = this.part,
      type = DownloadRequest.Type.SINGLE
    )

  @Volatile
  private var progressValue = 0.0

  override val progress: Double
    get() = this.progressValue

  override fun cancel() {
    this.stateField = PlayerDownloadTaskStatus.IdleNotDownloaded
    this.downloadEngine.delete(this.request)
  }

  override fun delete() {
    this.stateField = PlayerDownloadTaskStatus.IdleNotDownloaded
    this.downloadEngine.delete(this.request)
  }

  override val status: PlayerDownloadTaskStatus
    get() = this.stateField

  override fun fetch() {
    this.stateField = PlayerDownloadTaskStatus.Downloading(
      if (this.progressValue == 0.0) {
        null
      } else {
        this.progressValue
      }
    )
    this.downloadEngine.download(this.request)
  }

  internal fun setProgress(percent: Double) {
    this.progressValue = percent
  }

  override val readingOrderItems: List<PlayerReadingOrderItemType>
    get() = this.spineElements
}
