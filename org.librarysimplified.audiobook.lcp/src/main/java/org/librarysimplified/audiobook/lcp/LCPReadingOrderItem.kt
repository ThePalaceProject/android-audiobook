package org.librarysimplified.audiobook.lcp

import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.open_access.ExoManifestMutableReadingOrderItem

/**
 * A spine element in an LCP audio book.
 */

class LCPReadingOrderItem(
  val itemManifest: ExoManifestMutableReadingOrderItem,
  internal var nextElement: PlayerReadingOrderItemType?,
  internal var previousElement: PlayerReadingOrderItemType?,
  @Volatile override var duration: Duration?,
) : PlayerReadingOrderItemType {

  private lateinit var bookActual: LCPAudioBook

  override val book: PlayerAudioBookType
    get() = this.bookActual

  override val next: PlayerReadingOrderItemType?
    get() = this.nextElement

  override val previous: PlayerReadingOrderItemType?
    get() = this.previousElement

  fun setBook(book: LCPAudioBook) {
    this.bookActual = book
  }

  override val downloadTasksSupported = false

  override val downloadStatus: PlayerReadingOrderItemDownloadStatus
    get() = PlayerReadingOrderItemDownloaded(this)

  override val id: PlayerManifestReadingOrderID =
    this.itemManifest.item.id

  override val index: Int =
    this.itemManifest.index

  override val startingPosition: PlayerPosition =
    PlayerPosition(this.id, 0L)
}
