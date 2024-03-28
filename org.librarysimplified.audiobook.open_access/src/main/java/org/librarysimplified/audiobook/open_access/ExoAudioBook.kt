package org.librarysimplified.audiobook.open_access

import android.app.Application
import android.content.Context
import io.reactivex.subjects.PublishSubject
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerDownloadWholeBookTaskType
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.api.extensions.PlayerExtensionType
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOC
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An ExoPlayer audio book.
 */

class ExoAudioBook private constructor(
  private val exoManifest: ExoManifest,
  override val downloadTasks: List<ExoDownloadTask>,
  override val downloadTasksByID: Map<PlayerManifestReadingOrderID, ExoDownloadTask>,
  private val context: Application,
  private val engineExecutor: ScheduledExecutorService,
  override val readingOrder: List<ExoReadingOrderItemHandle>,
  override val readingOrderByID: Map<PlayerManifestReadingOrderID, ExoReadingOrderItemHandle>,
  override val readingOrderElementDownloadStatus: PublishSubject<PlayerReadingOrderItemDownloadStatus>
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
  ): CompletableFuture<Unit> {
    val future = CompletableFuture<Unit>()
    this.engineExecutor.execute {
      try {
        future.complete(this.replaceManifestTransform(manifest))
      } catch (e: Throwable) {
        future.completeExceptionally(e)
      }
    }
    return future
  }

  override val tableOfContents: PlayerManifestTOC
    get() = this.exoManifest.toc

  override val manifest: PlayerManifest
    get() = this.exoManifest.originalManifest

  private fun replaceManifestTransform(
    manifest: PlayerManifest
  ) {
    this.logger.debug("Replacing manifest")
    return when (val result = ExoManifest.transform(
      context = context,
      bookID = this.id,
      manifest = manifest
    )) {
      is PlayerResult.Success ->
        this.replaceManifestWith(result.result)

      is PlayerResult.Failure ->
        throw result.failure
    }
  }

  private fun replaceManifestWith(
    exoManifest: ExoManifest
  ) {
    if (exoManifest.bookID != this.exoManifest.bookID) {
      throw IllegalArgumentException(
        "Manifest ID ${exoManifest.bookID} does not match existing id ${this.exoManifest.bookID}"
      )
    }

    if (exoManifest.readingOrderItems.size != this.exoManifest.readingOrderItems.size) {
      throw IllegalArgumentException(
        "Manifest spine item count ${exoManifest.readingOrderItems.size} does not match existing count ${this.exoManifest.readingOrderItems.size}"
      )
    }

    for (index in exoManifest.readingOrderItems.indices) {
      this.logger.debug("[{}] Updated URI", index)
      val oldSpine = this.exoManifest.readingOrderItems[index]
      val newSpine = exoManifest.readingOrderItems[index]
      oldSpine.item = newSpine.item
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
      context: Application,
      engineExecutor: ScheduledExecutorService,
      manifest: ExoManifest,
      downloadProvider: PlayerDownloadProviderType,
      extensions: List<PlayerExtensionType>,
      userAgent: PlayerUserAgent
    ): PlayerAudioBookType {
      val directory = this.findDirectoryFor(context, manifest.bookID)
      this.log.debug("Book directory: {}", directory)

      /*
       * Set up all the various bits of state required.
       */

      val statusEvents: PublishSubject<PlayerReadingOrderItemDownloadStatus> =
        PublishSubject.create()
      val handles =
        ArrayList<ExoReadingOrderItemHandle>()
      val handlesById =
        HashMap<PlayerManifestReadingOrderID, ExoReadingOrderItemHandle>()

      var handlePrevious: ExoReadingOrderItemHandle? = null

      /*
       * Build a doubly-linked list of manifest item handles.
       */

      for (manifestItem in manifest.readingOrderItems) {
        val handle =
          ExoReadingOrderItemHandle(
            downloadStatusEvents = statusEvents,
            itemManifest = manifestItem,
            previousElement = handlePrevious,
            nextElement = null,
            duration = null
          )

        handles.add(handle)
        handlesById.put(handle.id, handle)

        if (handlePrevious != null) {
          handlePrevious.nextElement = handle
        }
        handlePrevious = handle
      }

      /*
       * Create download tasks for each reading order item.
       */

      val downloadTasksById =
        HashMap<PlayerManifestReadingOrderID, ExoDownloadTask>()
      val downloadTasks =
        manifest.readingOrderItems.map { item ->
          val partFile =
            File(directory, "${item.index}.part")
          val task =
            ExoDownloadTask(
              downloadProvider = downloadProvider,
              downloadStatusExecutor = engineExecutor,
              extensions = extensions,
              originalLink = item.item.link,
              partFile = partFile,
              readingOrderItems = handles,
              userAgent = userAgent,
            )

          downloadTasksById.put(item.item.id, task)
          task
        }

      val book =
        ExoAudioBook(
          context = context,
          downloadTasks = downloadTasks.toList(),
          downloadTasksByID = downloadTasksById.toMap(),
          engineExecutor = engineExecutor,
          exoManifest = manifest,
          readingOrder = handles.toList(),
          readingOrderByID = handlesById.toMap(),
          readingOrderElementDownloadStatus = statusEvents
        )

      for (e in handles) {
        e.setBook(book)
      }

      return book
    }
  }

  override fun close() {
    if (this.isClosedNow.compareAndSet(false, true)) {
      this.logger.debug("Closed audio book")
      this.manifestUpdates.onComplete()
      this.readingOrderElementDownloadStatus.onComplete()
    }
  }

  override val isClosed: Boolean
    get() = this.isClosedNow.get()

  override val id: PlayerBookID =
    this.exoManifest.bookID
}
