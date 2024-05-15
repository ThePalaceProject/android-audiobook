package org.librarysimplified.audiobook.media3

import io.reactivex.subjects.PublishSubject
import net.jcip.annotations.GuardedBy
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemNotDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsAbsoluteInterval
import org.librarysimplified.audiobook.manifest.api.PlayerMillisecondsReadingOrderItem

/**
 * A spine element in an audio book.
 */

class ExoReadingOrderItemHandle(
  private val downloadStatusEvents: PublishSubject<PlayerReadingOrderItemDownloadStatus>,
  val itemManifest: ExoManifestMutableReadingOrderItem,
  val interval: PlayerMillisecondsAbsoluteInterval,
  internal var nextElement: ExoReadingOrderItemHandle?,
  internal var previousElement: ExoReadingOrderItemHandle?
) : PlayerReadingOrderItemType {

  /**
   * The current download status of the spine element.
   */

  private val statusLock: Any = Object()

  @GuardedBy("statusLock")
  private var statusNow: PlayerReadingOrderItemDownloadStatus =
    PlayerReadingOrderItemNotDownloaded(this)

  private lateinit var bookActual: ExoAudioBook

  override val book: PlayerAudioBookType
    get() = this.bookActual

  override val next: ExoReadingOrderItemHandle?
    get() = this.nextElement

  override val previous: ExoReadingOrderItemHandle?
    get() = this.previousElement

  override val duration: Duration
    get() = Duration.millis(this.interval.size().value)

  fun setBook(book: ExoAudioBook) {
    this.bookActual = book
  }

  fun setDownloadStatus(status: PlayerReadingOrderItemDownloadStatus) {
    synchronized(this.statusLock, { this.statusNow = status })
    this.downloadStatusEvents.onNext(status)
  }

  override val downloadTasksSupported: Boolean
    get() = true

  override val downloadStatus: PlayerReadingOrderItemDownloadStatus
    get() = synchronized(this.statusLock, { this.statusNow })

  override val id: PlayerManifestReadingOrderID =
    this.itemManifest.item.id

  override val index: Int =
    this.itemManifest.index

  override val startingPosition: PlayerPosition =
    PlayerPosition(this.id, PlayerMillisecondsReadingOrderItem(0L))
}
