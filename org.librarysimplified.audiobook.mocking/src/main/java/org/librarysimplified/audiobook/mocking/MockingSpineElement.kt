package org.librarysimplified.audiobook.mocking

import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus
import org.librarysimplified.audiobook.api.PlayerSpineElementType
import rx.subjects.BehaviorSubject

/**
 * A fake spine element in a fake audio book.
 */

class MockingSpineElement(
  val bookMocking: MockingAudioBook,
  val downloadStatusEvents: BehaviorSubject<PlayerSpineElementDownloadStatus>,
  override val index: Int,
  override val duration: Duration,
  override val id: String,
  override val title: String
) : PlayerSpineElementType {

  var downloadTasksAreSupported = true

  override val downloadTasksSupported: Boolean
    get() = this.downloadTasksAreSupported

  override val book: PlayerAudioBookType
    get() = this.bookMocking

  override val next: PlayerSpineElementType?
    get() =
      if (this.index + 1 < this.bookMocking.spineItems.size) {
        this.bookMocking.spineItems[this.index + 1]
      } else {
        null
      }

  override val previous: PlayerSpineElementType?
    get() =
      if (this.index > 0) {
        this.bookMocking.spineItems[this.index - 1]
      } else {
        null
      }

  override val position: PlayerPosition
    get() = PlayerPosition(title = this.title, part = 0, chapter = this.index + 1, startOffset = 0L, currentOffset = 0L)

  private var downloadStatusValue: PlayerSpineElementDownloadStatus =
    PlayerSpineElementDownloadStatus.PlayerSpineElementNotDownloaded(this)

  fun setDownloadStatus(status: PlayerSpineElementDownloadStatus) {
    this.downloadStatusValue = status
    this.downloadStatusEvents.onNext(status)
  }

  override val downloadStatus: PlayerSpineElementDownloadStatus
    get() = this.downloadStatusValue
}
