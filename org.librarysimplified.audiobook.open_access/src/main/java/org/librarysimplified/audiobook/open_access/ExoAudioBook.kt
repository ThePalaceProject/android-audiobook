package org.librarysimplified.audiobook.open_access

import android.content.Context
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import org.joda.time.Duration
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerDownloadWholeBookTaskType
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerSpineElementDownloadStatus
import org.librarysimplified.audiobook.api.PlayerSpineElementType
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.api.extensions.PlayerExtensionType
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.slf4j.LoggerFactory
import rx.subjects.PublishSubject
import java.io.File
import java.util.SortedMap
import java.util.TreeMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An ExoPlayer audio book.
 */

class ExoAudioBook private constructor(
  private val manifest: ExoManifest,
  override val downloadTasks: List<ExoDownloadTask>,
  private val context: Context,
  private val engineExecutor: ScheduledExecutorService,
  override val spine: List<ExoSpineElement>,
  override val spineByID: Map<String, ExoSpineElement>,
  override val spineByPartAndChapter: SortedMap<Int, SortedMap<Int, PlayerSpineElementType>>,
  override val spineElementDownloadStatus: PublishSubject<PlayerSpineElementDownloadStatus>,
  override val id: PlayerBookID,
) : PlayerAudioBookType {

  private val logger = LoggerFactory.getLogger(ExoAudioBook::class.java)
  private val isClosedNow = AtomicBoolean(false)
  private val wholeBookTask = ExoDownloadWholeBookTask(this)

  private val manifestUpdates =
    PublishSubject.create<Unit>()
      .toSerialized()

  override fun createPlayer(): PlayerType {
    check(!this.isClosed) { "Audio book has been closed" }

    return ExoAudioBookPlayer.create(
      book = this,
      context = this.context,
      engineExecutor = this.engineExecutor,
      manifestUpdates = this.manifestUpdates
    )
  }

  override val supportsStreaming: Boolean
    get() = false

  override val supportsIndividualChapterDeletion: Boolean
    get() = true

  override val wholeBookDownloadTask: PlayerDownloadWholeBookTaskType
    get() = this.wholeBookTask

  override fun replaceManifest(
    manifest: PlayerManifest
  ): ListenableFuture<Unit> {
    val future = SettableFuture.create<Unit>()
    this.engineExecutor.execute {
      try {
        this.replaceManifestTransform(manifest)
        future.set(Unit)
      } catch (e: Exception) {
        future.setException(e)
      }
    }
    return future
  }

  private fun replaceManifestTransform(
    manifest: PlayerManifest
  ) {
    this.logger.debug("replacing manifest")
    return when (val result = ExoManifest.transform(context, manifest)) {
      is PlayerResult.Success ->
        this.replaceManifestWith(result.result)
      is PlayerResult.Failure ->
        throw result.failure
    }
  }

  private fun replaceManifestWith(
    exoManifest: ExoManifest
  ) {
    if (exoManifest.id != this.manifest.id) {
      throw IllegalArgumentException(
        "Manifest ID ${exoManifest.id} does not match existing id ${this.manifest.id}"
      )
    }

    if (exoManifest.spineItems.size != this.manifest.spineItems.size) {
      throw IllegalArgumentException(
        "Manifest spine item count ${exoManifest.spineItems.size} does not match existing count ${this.manifest.spineItems.size}"
      )
    }

    for (index in exoManifest.spineItems.indices) {
      this.logger.debug("[{}] updated URI", index)
      val oldSpine = this.manifest.spineItems[index]
      val newSpine = exoManifest.spineItems[index]
      oldSpine.updateLink(newSpine.originalLink, newSpine.uri)
    }

    this.logger.debug("sending manifest update event")
    this.manifestUpdates.onNext(Unit)
  }

  companion object {

    private val log = LoggerFactory.getLogger(ExoAudioBook::class.java)

    private fun findDirectoryFor(context: Context, id: PlayerBookID): File {
      val base = context.filesDir
      val all = File(base, "exoplayer_audio")
      return File(all, id.value)
    }

    fun create(
      context: Context,
      engineExecutor: ScheduledExecutorService,
      manifest: ExoManifest,
      downloadProvider: PlayerDownloadProviderType,
      extensions: List<PlayerExtensionType>,
      userAgent: PlayerUserAgent
    ): PlayerAudioBookType {
      val bookId = PlayerBookID.transform(manifest.id)
      val directory = this.findDirectoryFor(context, bookId)
      this.log.debug("book directory: {}", directory)

      /*
       * Set up all the various bits of state required.
       */

      val statusEvents: PublishSubject<PlayerSpineElementDownloadStatus> = PublishSubject.create()
      val elements = ArrayList<ExoSpineElement>()
      val elementsById = HashMap<String, ExoSpineElement>()
      val elementsByPart = TreeMap<Int, TreeMap<Int, PlayerSpineElementType>>()

      var manifestIndex = 0
      var spineItemPrevious: ExoSpineElement? = null

      val downloadTasks = manifest.spineItems.groupBy { manifestSpineItem ->
        manifestSpineItem.originalLink
      }.map { entry ->

        val originalLink = entry.key
        val spineItems = entry.value.map { manifestSpineItem ->

          val duration =
            manifestSpineItem.duration?.let { time ->
              Duration.standardSeconds(Math.floor(time).toLong())
            }

          val spineItem = ExoSpineElement(
            downloadStatusEvents = statusEvents,
            bookID = bookId,
            itemManifest = manifestSpineItem,
            index = manifestIndex,
            nextElement = null,
            previousElement = spineItemPrevious,
            duration = duration
          )

          elements.add(spineItem)
          elementsById[spineItem.id] = spineItem
          this.addElementByPartAndChapter(elementsByPart, spineItem)

          /*
           * Make the "next" field of the previous element point to the current element.
           */
          val previous = spineItemPrevious
          if (previous != null) {
            previous.nextElement = spineItem
          }
          spineItemPrevious = spineItem

          spineItem
        }

        manifestIndex++

        val partFile =
          File(directory, "$manifestIndex.part")

        ExoDownloadTask(
          downloadStatusExecutor = engineExecutor,
          downloadProvider = downloadProvider,
          originalLink = originalLink,
          partFile = partFile,
          spineElements = spineItems,
          userAgent = userAgent,
          extensions = extensions
        )
      }

      val book =
        ExoAudioBook(
          context = context,
          engineExecutor = engineExecutor,
          id = bookId,
          manifest = manifest,
          spine = elements,
          spineByID = elementsById,
          spineByPartAndChapter = elementsByPart as SortedMap<Int, SortedMap<Int, PlayerSpineElementType>>,
          spineElementDownloadStatus = statusEvents,
          downloadTasks = downloadTasks
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
      element: ExoSpineElement
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
      this.manifestUpdates.onCompleted()
      this.spineElementDownloadStatus.onCompleted()
    }
  }

  override val isClosed: Boolean
    get() = this.isClosedNow.get()
}
