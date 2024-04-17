package org.librarysimplified.audiobook.mocking

import io.reactivex.subjects.BehaviorSubject
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID

/**
 * A fake spine element in a fake audio book.
 */

class MockingReadingOrderItem(
  val bookMocking: MockingAudioBook,
  val downloadStatusEvents: BehaviorSubject<PlayerReadingOrderItemDownloadStatus>,
  override val index: Int,
  override val duration: Duration,
  override val id: PlayerManifestReadingOrderID
) : PlayerReadingOrderItemType {

  var downloadTasksAreSupported = true

  override val downloadTasksSupported: Boolean
    get() = this.downloadTasksAreSupported

  override val startingPosition: PlayerPosition
    get() = PlayerPosition(this.id, 0L)

  override val book: PlayerAudioBookType
    get() = this.bookMocking

  override val next: PlayerReadingOrderItemType?
    get() =
      if (this.index + 1 < this.bookMocking.spineItems.size) {
        this.bookMocking.spineItems[this.index + 1]
      } else {
        null
      }

  override val previous: PlayerReadingOrderItemType?
    get() =
      if (this.index > 0) {
        this.bookMocking.spineItems[this.index - 1]
      } else {
        null
      }

  private var downloadStatusValue: PlayerReadingOrderItemDownloadStatus =
    PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemNotDownloaded(this)

  fun setDownloadStatus(status: PlayerReadingOrderItemDownloadStatus) {
    this.downloadStatusValue = status
    this.downloadStatusEvents.onNext(status)
  }

  override val downloadStatus: PlayerReadingOrderItemDownloadStatus
    get() = this.downloadStatusValue
}
