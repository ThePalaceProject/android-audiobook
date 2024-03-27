package org.librarysimplified.audiobook.open_access

import io.reactivex.subjects.PublishSubject
import net.jcip.annotations.GuardedBy
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemNotDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID

/**
 * A spine element in an audio book.
 */

class ExoReadingOrderItemHandle(
  private val downloadStatusEvents: PublishSubject<PlayerReadingOrderItemDownloadStatus>,
  val itemManifest: ExoManifestMutableReadingOrderItem,
  internal var nextElement: PlayerReadingOrderItemType?,
  internal var previousElement: PlayerReadingOrderItemType?,
  @Volatile override var duration: Duration?
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

  override val next: PlayerReadingOrderItemType?
    get() = this.nextElement

  override val previous: PlayerReadingOrderItemType?
    get() = this.previousElement

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
    PlayerPosition(this.id, 0L)
}
