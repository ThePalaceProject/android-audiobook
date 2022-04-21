package org.librarysimplified.audiobook.lcp

import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerDownloadTaskType
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementDownloaded
import org.librarysimplified.audiobook.api.PlayerSpineElementType
import org.librarysimplified.audiobook.open_access.ExoManifestSpineItem

/**
 * A spine element in an LCP audio book.
 */

class LCPSpineElement(
  private val bookID: PlayerBookID,
  val itemManifest: ExoManifestSpineItem,
  override val index: Int,
  internal var nextElement: PlayerSpineElementType?,
  internal var previousElement: PlayerSpineElementType?,
  @Volatile override var duration: Duration?,
) : PlayerSpineElementType {

  private lateinit var bookActual: LCPAudioBook

  override val book: PlayerAudioBookType
    get() = this.bookActual

  override val next: PlayerSpineElementType?
    get() = this.nextElement

  override val previous: PlayerSpineElementType?
    get() = this.previousElement

  override val position: PlayerPosition
    get() = PlayerPosition(
      this.itemManifest.title,
      this.itemManifest.part,
      this.itemManifest.chapter,
      if (this.itemManifest.offset != null) {
        (this.itemManifest.offset!! * 1000.0).toLong()
      } else {
        0L
      }
    )

  override val title: String?
    get() = this.itemManifest.title

  /**
   * LCP-protected books are packaged, so the whole book will have been downloaded before being
   * handed to the player. This downloadTask simply no-ops all of its methods, and reports that
   * download is complete.
   */

  private val downloadTask = object : PlayerDownloadTaskType {
    override fun fetch() {}
    override fun cancel() {}
    override fun delete() {}
    override val progress = 1.0
  }

  override fun downloadTask(): PlayerDownloadTaskType {
    return this.downloadTask
  }

  fun setBook(book: LCPAudioBook) {
    this.bookActual = book
  }

  override val downloadTasksSupported = false

  override val downloadStatus: PlayerSpineElementDownloadStatus
    get() = PlayerSpineElementDownloaded(this)

  override val id: String
    get() = String.format("%s-%d", this.bookID.value, this.index)
}
