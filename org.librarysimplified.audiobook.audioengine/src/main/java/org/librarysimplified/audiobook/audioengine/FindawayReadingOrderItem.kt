package org.librarysimplified.audiobook.audioengine

import io.reactivex.subjects.Subject
import net.jcip.annotations.GuardedBy
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsAbsoluteInterval
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsReadingOrderItem

/**
 * A spine element in an audio book.
 */

class FindawayReadingOrderItem(
  private val downloadStatusEvents: Subject<PlayerReadingOrderItemDownloadStatus>,
  val itemManifest: FindawayManifestMutableReadingOrderItem,
  override val index: Int,
  var nextElement: PlayerReadingOrderItemType?,
  var prevElement: PlayerReadingOrderItemType?,
  val interval: PlayerMillisecondsAbsoluteInterval
) : PlayerReadingOrderItemType {

  override fun toString(): String {
    return StringBuilder(128)
      .append("[FindawayReadingOrderItem ")
      .append(this.index)
      .append(' ')
      .append(this.itemManifest)
      .append(']')
      .toString()
  }

  /**
   * The current download status of the spine element.
   */

  private val statusLock: Any = Object()

  @GuardedBy("statusLock")
  private var statusNow: PlayerReadingOrderItemDownloadStatus =
    PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemNotDownloaded(this)

  @GuardedBy("statusLock")
  private var statusPrevious: PlayerReadingOrderItemDownloadStatus =
    PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemNotDownloaded(this)

  private lateinit var bookActual: FindawayAudioBook

  override val book: PlayerAudioBookType
    get() = this.bookActual

  override val next: PlayerReadingOrderItemType?
    get() = this.nextElement

  override val previous: PlayerReadingOrderItemType?
    get() = this.prevElement

  override val duration: Duration
    get() = Duration.millis(this.interval.size().value)

  override val id: PlayerManifestReadingOrderID
    get() = this.itemManifest.id

  fun setBook(book: FindawayAudioBook) {
    this.bookActual = book
  }

  fun setDownloadStatus(status: PlayerReadingOrderItemDownloadStatus) {
    synchronized(this.statusLock) {
      this.statusPrevious = this.statusNow
      this.statusNow = status
    }
    this.downloadStatusEvents.onNext(status)
  }

  override val downloadTasksSupported: Boolean =
    true

  override val startingPosition: PlayerPosition
    get() = PlayerPosition(
      readingOrderID = this.itemManifest.id,
      offsetMilliseconds = PlayerMillisecondsReadingOrderItem(0L)
    )

  override val downloadStatus: PlayerReadingOrderItemDownloadStatus
    get() = synchronized(this.statusLock) { this.statusNow }

  override val downloadStatusPrevious: PlayerReadingOrderItemDownloadStatus
    get() = synchronized(this.statusLock) { this.statusPrevious }
}
