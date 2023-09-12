package org.librarysimplified.audiobook.lcp

import android.content.Context
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerDownloadTaskType
import org.librarysimplified.audiobook.api.PlayerDownloadWholeBookTaskType
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus
import org.librarysimplified.audiobook.api.PlayerSpineElementType
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.open_access.ExoManifest
import org.readium.r2.shared.publication.ContentProtection
import org.slf4j.LoggerFactory
import rx.subjects.PublishSubject
import java.io.File
import java.util.SortedMap
import java.util.TreeMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An LCP audio book.
 */

class LCPAudioBook private constructor(
  private val context: Context,
  private val engineExecutor: ScheduledExecutorService,
  override val spine: List<LCPSpineElement>,
  override val spineByID: Map<String, LCPSpineElement>,
  override val spineByPartAndChapter: SortedMap<Int, SortedMap<Int, PlayerSpineElementType>>,
  override val spineElementDownloadStatus: PublishSubject<PlayerSpineElementDownloadStatus>,
  override val id: PlayerBookID,
  val file: File,
  val contentProtections: List<ContentProtection>,
  val manualPassphrase: Boolean
) : PlayerAudioBookType {

  private val logger = LoggerFactory.getLogger(LCPAudioBook::class.java)
  private val isClosedNow = AtomicBoolean(false)

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
    override val spineItems = listOf<PlayerSpineElementType>()
    override fun fulfillsSpineElement(spineElement: PlayerSpineElementType) = false
  }

  override fun replaceManifest(
    manifest: PlayerManifest
  ): ListenableFuture<Unit> {
    return Futures.immediateFuture(null)
  }

  companion object {
    fun create(
      context: Context,
      engineExecutor: ScheduledExecutorService,
      manifest: ExoManifest,
      file: File,
      contentProtections: List<ContentProtection>,
      manualPassphrase: Boolean
    ): PlayerAudioBookType {
      val bookId = PlayerBookID.transform(manifest.id)

      /*
       * Set up all the various bits of state required.
       */

      val statusEvents: PublishSubject<PlayerSpineElementDownloadStatus> = PublishSubject.create()
      val elements = ArrayList<LCPSpineElement>()
      val elementsById = HashMap<String, LCPSpineElement>()
      val elementsByPart = TreeMap<Int, TreeMap<Int, PlayerSpineElementType>>()

      var index = 0
      var spineItemPrevious: LCPSpineElement? = null

      manifest.spineItems.forEach { spineItem ->
        val duration =
          spineItem.duration?.let { time ->
            Duration.standardSeconds(Math.floor(time).toLong())
          }

        val element =
          LCPSpineElement(
            bookID = bookId,
            itemManifest = spineItem,
            index = index,
            nextElement = null,
            previousElement = spineItemPrevious,
            duration = duration,
          )

        elements.add(element)
        elementsById.put(element.id, element)
        this.addElementByPartAndChapter(elementsByPart, element)
        ++index

        /*
         * Make the "next" field of the previous element point to the current element.
         */

        val previous = spineItemPrevious

        if (previous != null) {
          previous.nextElement = element
        }

        spineItemPrevious = element
      }

      val book =
        LCPAudioBook(
          context = context,
          engineExecutor = engineExecutor,
          id = bookId,
          spine = elements,
          spineByID = elementsById,
          spineByPartAndChapter = elementsByPart as SortedMap<Int, SortedMap<Int, PlayerSpineElementType>>,
          spineElementDownloadStatus = statusEvents,
          file = file,
          contentProtections = contentProtections,
          manualPassphrase = manualPassphrase
        )

      for (e in elements) {
        e.setBook(book)
      }

      return book
    }

    /**
     * Organize an element by part number and chapter number.
     */

    private fun addElementByPartAndChapter(
      elementsByPart: TreeMap<Int, TreeMap<Int, PlayerSpineElementType>>,
      element: LCPSpineElement
    ) {
      val partChapters: TreeMap<Int, PlayerSpineElementType> =
        if (elementsByPart.containsKey(element.itemManifest.part)) {
          elementsByPart[element.itemManifest.part]!!
        } else {
          TreeMap()
        }

      partChapters.put(element.itemManifest.chapter, element)
      elementsByPart.put(element.itemManifest.part, partChapters)
    }
  }

  override fun close() {
    if (this.isClosedNow.compareAndSet(false, true)) {
      this.logger.debug("closed audio book")
      this.spineElementDownloadStatus.onCompleted()
    }
  }

  override val downloadTasks: List<PlayerDownloadTaskType>
    get() = emptyList()

  override val isClosed: Boolean
    get() = this.isClosedNow.get()
}
