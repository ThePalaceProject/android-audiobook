package org.librarysimplified.audiobook.open_access

import net.jcip.annotations.GuardedBy
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus.PlayerSpineElementNotDownloaded
import org.librarysimplified.audiobook.api.PlayerSpineElementType
import rx.subjects.PublishSubject

/**
 * A spine element in an audio book.
 */

class ExoSpineElement(
  private val downloadStatusEvents: PublishSubject<PlayerSpineElementDownloadStatus>,
  private val bookID: PlayerBookID,
  val itemManifest: ExoManifestSpineItem,
  override val index: Int,
  internal var nextElement: PlayerSpineElementType?,
  internal var previousElement: PlayerSpineElementType?,
  @Volatile override var duration: Duration?
) : PlayerSpineElementType {

  /**
   * The current download status of the spine element.
   */

  private val statusLock: Any = Object()
  @GuardedBy("statusLock")
  private var statusNow: PlayerSpineElementDownloadStatus =
    PlayerSpineElementNotDownloaded(this)

  private lateinit var bookActual: ExoAudioBook

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
        (this.itemManifest.offset * 1000.0).toLong()
      } else {
        0L
      }
    )

  override val title: String?
    get() = this.itemManifest.title

  fun setBook(book: ExoAudioBook) {
    this.bookActual = book
  }

  fun setDownloadStatus(status: PlayerSpineElementDownloadStatus) {
    synchronized(this.statusLock, { this.statusNow = status })
    this.downloadStatusEvents.onNext(status)
  }

  override val downloadTasksSupported: Boolean
    get() = true

  override val downloadStatus: PlayerSpineElementDownloadStatus
    get() = synchronized(this.statusLock, { this.statusNow })

  override val id: String
    get() = String.format("%s-%d", this.bookID.value, this.index)
}
