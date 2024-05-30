package org.librarysimplified.audiobook.media3

import android.app.Application
import android.content.Context
import androidx.media3.datasource.DataSource
import io.reactivex.subjects.PublishSubject
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerDownloadProviderType
import org.librarysimplified.audiobook.api.PlayerDownloadTaskType
import org.librarysimplified.audiobook.api.PlayerDownloadWholeBookTaskType
import org.librarysimplified.audiobook.api.PlayerMissingTrackNameGeneratorType
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
  override val downloadTasks: List<PlayerDownloadTaskType>,
  override val downloadTasksByID: Map<PlayerManifestReadingOrderID, PlayerDownloadTaskType>,
  private val context: Application,
  private val engineExecutor: ScheduledExecutorService,
  private val dataSourceFactory: DataSource.Factory,
  override val readingOrder: List<ExoReadingOrderItemHandle>,
  override val readingOrderByID: Map<PlayerManifestReadingOrderID, ExoReadingOrderItemHandle>,
  override val readingOrderElementDownloadStatus: PublishSubject<PlayerReadingOrderItemDownloadStatus>,
  private val missingTrackNameGenerator: PlayerMissingTrackNameGeneratorType,
  private val supportsDownloads: Boolean
) : PlayerAudioBookType {

  private val logger =
    LoggerFactory.getLogger(ExoAudioBook::class.java)

  private val isClosedNow =
    AtomicBoolean(false)

  private val manifestUpdates =
    PublishSubject.create<Unit>()
      .toSerialized()

  override val wholeBookDownloadTask: PlayerDownloadWholeBookTaskType =
    ExoDownloadWholeBookTask(this)

  override fun createPlayer(): PlayerType {
    check(!this.isClosed) { "Audio book has been closed" }

    return ExoAudioBookPlayer.create(
      book = this,
      context = this.context,
      manifestUpdates = this.manifestUpdates,
      dataSourceFactory = this.dataSourceFactory
    )
  }

  override val supportsStreaming: Boolean =
    true

  override val supportsIndividualChapterDeletion: Boolean =
    this.supportsDownloads

  override val supportsIndividualChapterDownload: Boolean =
    this.supportsDownloads

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

    val newBookID = PlayerBookID.transform(manifest.metadata.identifier)
    if (this.id != newBookID) {
      val sb = StringBuilder()
      sb.append("Manifest book IDs must match!\n")
      sb.append("  Incoming manifest identifier: ${manifest.metadata.identifier}\n")
      sb.append("  Incoming book ID:             ${newBookID.value}")
      sb.append("  This manifest identifier:     ${this.manifest.metadata.identifier}\n")
      sb.append("  This book ID:                 ${this.id.value}\n")
      throw IllegalArgumentException(sb.toString())
    }

    return when (val result = ExoManifest.transform(
      bookID = this.id,
      manifest = manifest,
      missingTrackNames = this.missingTrackNameGenerator
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
      userAgent: PlayerUserAgent,
      dataSourceFactory: DataSource.Factory,
      missingTrackNameGenerator: PlayerMissingTrackNameGeneratorType,
      supportsDownloads: Boolean
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
      val downloadTasksById =
        HashMap<PlayerManifestReadingOrderID, PlayerDownloadTaskType>()
      val downloadTasks =
        ArrayList<PlayerDownloadTaskType>()

      var handlePrevious: ExoReadingOrderItemHandle? = null

      /*
       * Build a doubly-linked list of manifest item handles, and create download
       * tasks for each reading order item.
       */

      for (manifestItem in manifest.readingOrderItems) {
        val handle =
          ExoReadingOrderItemHandle(
            downloadStatusEvents = statusEvents,
            itemManifest = manifestItem,
            previousElement = handlePrevious,
            nextElement = null,
            interval = manifest.toc.readingOrderIntervals[manifestItem.item.id]!!
          )

        handles.add(handle)
        handlesById.put(handle.id, handle)

        if (handlePrevious != null) {
          handlePrevious.nextElement = handle
        }
        handlePrevious = handle

        val partFile =
          File(directory, "${manifestItem.index}.part")

        val task =
          if (supportsDownloads) {
            ExoDownloadTask(
              downloadProvider = downloadProvider,
              downloadStatusExecutor = engineExecutor,
              extensions = extensions,
              originalLink = manifestItem.item.link,
              partFile = partFile,
              readingOrderItem = handle,
              userAgent = userAgent,
              index = manifestItem.index
            )
          } else {
            ExoDownloadAlwaysSucceededTask(
              index = manifestItem.index,
              readingOrderItem = handle,
              playbackURI = manifestItem.item.link.hrefURI!!
            )
          }

        downloadTasksById.put(manifestItem.item.id, task)
        downloadTasks.add(task)
      }

      val book =
        ExoAudioBook(
          exoManifest = manifest,
          downloadTasks = downloadTasks.toList(),
          downloadTasksByID = downloadTasksById.toMap(),
          context = context,
          engineExecutor = engineExecutor,
          dataSourceFactory = dataSourceFactory,
          readingOrder = handles.toList(),
          readingOrderByID = handlesById.toMap(),
          readingOrderElementDownloadStatus = statusEvents,
          missingTrackNameGenerator = missingTrackNameGenerator,
          supportsDownloads = supportsDownloads
        )

      for (e in handles) {
        e.setBook(book)
      }

      return book
    }
  }

  override fun close() {
    if (this.isClosedNow.compareAndSet(false, true)) {
      try {
        this.logger.debug("Closing audio book")

        try {
          this.wholeBookDownloadTask.cancel()
        } catch (e: Exception) {
          this.logger.error("Failed to cancel download task: ", e)
        }

        for (task in this.downloadTasks) {
          try {
            task.cancel()
          } catch (e: Exception) {
            this.logger.error("Failed to cancel download task: ", e)
          }
        }

        this.manifestUpdates.onComplete()
        this.readingOrderElementDownloadStatus.onComplete()
      } finally {
        this.logger.debug("Closed audio book")
      }
    }
  }

  override val isClosed: Boolean
    get() = this.isClosedNow.get()

  override val id: PlayerBookID =
    this.exoManifest.bookID
}
