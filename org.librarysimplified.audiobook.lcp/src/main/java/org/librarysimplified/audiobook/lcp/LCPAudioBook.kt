package org.librarysimplified.audiobook.lcp

import android.app.Application
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.reactivex.subjects.PublishSubject
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerDownloadTaskType
import org.librarysimplified.audiobook.api.PlayerDownloadWholeBookTaskType
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.open_access.ExoManifest
import org.readium.r2.shared.publication.protection.ContentProtection
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An LCP audio book.
 */

class LCPAudioBook private constructor(
  private val manifest: ExoManifest,
  private val context: Application,
  private val engineExecutor: ScheduledExecutorService,
  override val readingOrder: List<LCPReadingOrderItem>,
  override val readingOrderByID: Map<PlayerManifestReadingOrderID, LCPReadingOrderItem>,
  override val readingOrderElementDownloadStatus: PublishSubject<PlayerReadingOrderItemDownloadStatus>,
  val file: File,
  val contentProtections: List<ContentProtection>,
  val manualPassphrase: Boolean
) : PlayerAudioBookType {

  private val logger =
    LoggerFactory.getLogger(LCPAudioBook::class.java)

  private val isClosedNow =
    AtomicBoolean(false)

  override fun createPlayer(): PlayerType {
    check(!this.isClosed) { "Audio book has been closed" }

    return LCPAudioBookPlayer.create(
      book = this,
      context = this.context,
      engineExecutor = this.engineExecutor,
      manualPassphrase = manualPassphrase
    )
  }

  override val supportsStreaming = false

  override val supportsIndividualChapterDeletion = false

  /**
   * LCP-protected books are packaged, so the whole book will have been downloaded before being
   * handed to the player. This wholeBookDownloadTask simply no-ops all of its methods, and reports
   * that download is complete.
   */

  override val wholeBookDownloadTask = object : PlayerDownloadWholeBookTaskType {
    override fun fetch() {}
    override fun cancel() {}
    override fun delete() {}
    override val progress = 1.0
    override val readingOrderItems: List<PlayerReadingOrderItemType> = listOf()
  }

  override fun replaceManifest(
    manifest: PlayerManifest
  ): ListenableFuture<Unit> {
    return Futures.immediateFuture(null)
  }

  companion object {
    fun create(
      context: Application,
      engineExecutor: ScheduledExecutorService,
      manifest: ExoManifest,
      file: File,
      contentProtections: List<ContentProtection>,
      manualPassphrase: Boolean
    ): PlayerAudioBookType {
      val statusEvents: PublishSubject<PlayerReadingOrderItemDownloadStatus> =
        PublishSubject.create()

      /*
       * Set up all the various bits of state required.
       */

      val handles =
        ArrayList<LCPReadingOrderItem>()
      val handlesById =
        HashMap<PlayerManifestReadingOrderID, LCPReadingOrderItem>()

      var handlePrevious: LCPReadingOrderItem? = null

      /*
       * Build a doubly-linked list of manifest item handles.
       */

      for (manifestItem in manifest.readingOrderItems) {
        val handle =
          LCPReadingOrderItem(
            itemManifest = manifestItem,
            nextElement = null,
            previousElement = handlePrevious,
            duration = null,
          )

        handles.add(handle)
        handlesById.put(handle.id, handle)

        if (handlePrevious != null) {
          handlePrevious.nextElement = handle
        }
        handlePrevious = handle
      }

      val book =
        LCPAudioBook(
          contentProtections = contentProtections,
          context = context,
          engineExecutor = engineExecutor,
          file = file,
          manifest = manifest,
          manualPassphrase = manualPassphrase,
          readingOrder = handles,
          readingOrderByID = handlesById,
          readingOrderElementDownloadStatus = statusEvents,
        )

      for (e in handles) {
        e.setBook(book)
      }

      return book
    }
  }

  override fun close() {
    if (this.isClosedNow.compareAndSet(false, true)) {
      this.logger.debug("closed audio book")
      this.readingOrderElementDownloadStatus.onComplete()
    }
  }

  override val downloadTasks: List<PlayerDownloadTaskType>
    get() = emptyList()

  override val downloadTasksByID: Map<PlayerManifestReadingOrderID, PlayerDownloadTaskType>
    get() = emptyMap()

  override val isClosed: Boolean
    get() = this.isClosedNow.get()

  override val id: PlayerBookID =
    this.manifest.bookID
}
