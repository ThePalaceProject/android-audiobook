package org.librarysimplified.audiobook.audioengine

import android.content.Context
import com.google.common.base.Preconditions
import io.audioengine.mobile.AudioEngine
import io.audioengine.mobile.AudioEngineException
import io.audioengine.mobile.DownloadStatus
import io.audioengine.mobile.DownloadStatus.DOWNLOADED
import io.audioengine.mobile.DownloadStatus.DOWNLOADING
import io.audioengine.mobile.DownloadStatus.NOT_DOWNLOADED
import io.audioengine.mobile.DownloadStatus.PAUSED
import io.audioengine.mobile.DownloadStatus.QUEUED
import io.audioengine.mobile.LogLevel
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerDownloadTaskType
import org.librarysimplified.audiobook.api.PlayerDownloadWholeBookTaskType
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloaded
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloading
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemNotDownloaded
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOC
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.SortedMap
import java.util.TreeMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A Findaway based implementation of the {@link PlayerAudioBookType} interface.
 */

class FindawayAudioBook private constructor(
  internal val findawayManifest: FindawayManifest,
  private val engine: AudioEngine,
  override val downloadTasks: List<PlayerDownloadTaskType>,
  override val readingOrder: List<FindawayReadingOrderItem>,
  override val readingOrderByID: Map<PlayerManifestReadingOrderID, FindawayReadingOrderItem>,
  internal val readingOrderByPartAndSequence: SortedMap<Int, SortedMap<Int, FindawayReadingOrderItem>>,
  override val readingOrderElementDownloadStatus: Subject<PlayerReadingOrderItemDownloadStatus>,
  override val id: PlayerBookID,
  internal val downloadEngine: FindawayDownloadEngineType,
  override val downloadTasksByID: Map<PlayerManifestReadingOrderID, PlayerDownloadTaskType>
) : PlayerAudioBookType {

  private val subscriptions: CompositeDisposable =
    CompositeDisposable()

  private val log =
    LoggerFactory.getLogger(FindawayAudioBook::class.java)

  private val isClosedNow =
    AtomicBoolean(false)

  private val wholeDownloadTask =
    FindawayDownloadWholeBookTask(this, this.downloadEngine, URI.create("urn:unsupported"))

  override val tableOfContents: PlayerManifestTOC =
    this.findawayManifest.toc

  override val manifest: PlayerManifest =
    this.findawayManifest.manifest

  override fun createPlayer(): PlayerType =
    FindawayPlayer(this, this.engine)

  override fun replaceManifest(
    manifest: PlayerManifest
  ): CompletableFuture<Unit> {
    val future = CompletableFuture<Unit>()
    future.completeExceptionally(
      UnsupportedOperationException("Manifest reloading is not supported")
    )
    return future
  }

  override val supportsStreaming: Boolean
    get() = true

  override val supportsIndividualChapterDeletion: Boolean
    get() = false

  override val supportsIndividualChapterDownload: Boolean
    get() = false

  init {
    if (this.readingOrder.size != this.readingOrderByID.size) {
      throw IllegalStateException(
        "Spine size " + this.readingOrder.size + " must match spineByID size " + this.readingOrderByID.size
      )
    }

    this.downloadTasks.map { task ->
      val findawayTask =
        task as FindawayChapterDownloadTask

      val statusObservable: Observable<DownloadStatus> =
        this.downloadEngine.status(
          contentId = this.findawayManifest.fulfillmentId,
          part = findawayTask.part,
          chapter = findawayTask.chapter
        )

      val subscription =
        statusObservable.subscribe(
          { status -> onDownloadStatus(findawayTask, status) },
          { error -> onDownloadError(error) },
          { })

      this.subscriptions.add(subscription)
    }
  }

  private fun onDownloadError(error: Throwable?) {
    log.error("onDownloadError: ", error)
  }

  private fun onDownloadStatus(
    task: FindawayChapterDownloadTask,
    status: DownloadStatus
  ) {
    when (status) {
      NOT_DOWNLOADED -> {
        task.readingOrderItems.forEach { element ->
          task.setProgress(0.0)
          val findawayElement = element as FindawayReadingOrderItem
          findawayElement.setDownloadStatus(PlayerReadingOrderItemNotDownloaded(findawayElement))
        }
      }

      QUEUED,
      DOWNLOADING,
      PAUSED -> {
        task.readingOrderItems.forEach { element ->
          val findawayElement = element as FindawayReadingOrderItem
          if (findawayElement.downloadStatus is PlayerReadingOrderItemDownloading) {
            // Ignore
          } else {
            findawayElement.setDownloadStatus(
              PlayerReadingOrderItemDownloading(findawayElement, 0)
            )
            task.setProgress(0.0)
          }
        }
      }

      DOWNLOADED -> {
        task.readingOrderItems.forEach { element ->
          val findawayElement = element as FindawayReadingOrderItem
          findawayElement.setDownloadStatus(PlayerReadingOrderItemDownloaded(findawayElement))
          task.setProgress(100.0)
        }
      }
    }
  }

  companion object {

    private val log = LoggerFactory.getLogger(FindawayAudioBook::class.java)

    /*
     * Create a new audio book from the given parsed manifest.
     */

    fun create(
      context: Context,
      manifest: FindawayManifest
    ): PlayerAudioBookType {
      announceBook(manifest)

      /*
       * Initialize the audio engine.
       */

      val engine = initializeAudioEngine(manifest, context)
        ?: throw FindawayInitializationException("Could not initialize AudioEngine")

      /*
       * Set up all the various bits of state required.
       */

      val statusEvents =
        PublishSubject.create<PlayerReadingOrderItemDownloadStatus>().toSerialized()
      val directDownloader =
        FindawayDownloadEngineDirect(engine.downloadEngine)

      val elements =
        ArrayList<FindawayReadingOrderItem>()
      val elementsById =
        HashMap<PlayerManifestReadingOrderID, FindawayReadingOrderItem>()
      val elementsByPart =
        TreeMap<Int, TreeMap<Int, FindawayReadingOrderItem>>()
      val downloadTasks =
        arrayListOf<FindawayChapterDownloadTask>()
      val downloadTasksById =
        HashMap<PlayerManifestReadingOrderID, FindawayChapterDownloadTask>()

      var index = 0
      var spineItemPrevious: FindawayReadingOrderItem? = null

      manifest.readingOrderItems.forEach { spineItem ->
        val interval =
          manifest.toc.readingOrderIntervals[spineItem.id]!!

        val element =
          FindawayReadingOrderItem(
            downloadStatusEvents = statusEvents,
            itemManifest = spineItem,
            index = index,
            nextElement = null,
            prevElement = spineItemPrevious,
            interval = interval
          )

        val downloadTask = FindawayChapterDownloadTask(
          downloadEngine = directDownloader,
          manifest = manifest,
          spineElements = listOf(element),
          chapter = spineItem.sequence,
          part = spineItem.part,
          index = index,
          playbackURI = URI.create(element.id.text)
        )

        downloadTasks.add(downloadTask)
        downloadTasksById[spineItem.id] = downloadTask

        elements.add(element)
        elementsById.put(element.id, element)
        addElementByPartAndChapter(elementsByPart, element)
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

      verifyElementsByPartSize(elementsByPart, manifest.readingOrderItems)

      val book =
        FindawayAudioBook(
          findawayManifest = manifest,
          engine = engine,
          downloadTasks = downloadTasks,
          readingOrder = elements,
          readingOrderByID = elementsById,
          readingOrderByPartAndSequence = elementsByPart as SortedMap<Int, SortedMap<Int, FindawayReadingOrderItem>>,
          readingOrderElementDownloadStatus = statusEvents,
          id = manifest.id,
          downloadEngine = directDownloader,
          downloadTasksByID = downloadTasksById
        )

      for (e in elements) {
        e.setBook(book)
      }

      return book
    }

    private fun verifyElementsByPartSize(
      elementsByPart: TreeMap<Int, TreeMap<Int, FindawayReadingOrderItem>>,
      spineItems: List<FindawayManifestMutableReadingOrderItem>
    ) {
      val expectedSize = spineItems.size
      var receivedSize = 0

      for (key in elementsByPart.keys) {
        val chapters = elementsByPart[key]!!
        receivedSize += chapters.size
      }

      Preconditions.checkArgument(
        expectedSize == receivedSize,
        "Findaway spine size %d matches manifest size %d",
        receivedSize,
        expectedSize
      )
    }

    private fun announceBook(manifest: FindawayManifest) {
      log.debug("Book title:       {}", manifest.title)
      log.debug("Book id:          {}", manifest.id)
      log.debug("Book spine size:  {}", manifest.readingOrderItems.size)
      log.debug("Book fulfill id:  {}", manifest.fulfillmentId)
      log.debug("Book account id:  {}", manifest.accountId)
      log.debug("Book checkout id: {}", manifest.checkoutId)
      log.debug("Book license id:  {}", manifest.licenseId)
    }

    private fun initializeAudioEngine(
      manifest: FindawayManifest,
      context: Context
    ): AudioEngine? {
      /*
       * XXX: We're refusing to call AudioEngine.init() multiple times because doing so breaks
       * the playback engine. However, it's always possible that the session key in the manifest
       * could change between calls..
       */

      try {
        return AudioEngine.getInstance()?.apply {
          setContext(context)
          setSessionId(manifest.sessionKey)
        }
      } catch (e: AudioEngineException) {
        log.debug("Initializing audio engine with session key {}", manifest.sessionKey)
        AudioEngine.init(context, manifest.sessionKey, LogLevel.VERBOSE)
        log.debug("Initialized audio engine with session key {}", manifest.sessionKey)
        return AudioEngine.getInstance()
      }
    }

    /**
     * Organize an element by part number and chapter number.
     */

    private fun addElementByPartAndChapter(
      elementsByPart: TreeMap<Int, TreeMap<Int, FindawayReadingOrderItem>>,
      element: FindawayReadingOrderItem
    ) {
      val partChapters: TreeMap<Int, FindawayReadingOrderItem> =
        if (elementsByPart.containsKey(element.itemManifest.part)) {
          elementsByPart[element.itemManifest.part]!!
        } else {
          TreeMap()
        }

      partChapters.put(element.itemManifest.sequence, element)
      elementsByPart.put(element.itemManifest.part, partChapters)
    }
  }

  override val isClosed: Boolean
    get() = this.isClosedNow.get()

  override fun close() {
    if (this.isClosedNow.compareAndSet(false, true)) {
      log.debug("Audio book closed")
      this.readingOrderElementDownloadStatus.onComplete()
      this.subscriptions.dispose()
    }
  }

  internal fun readingOrderForPartAndSequence(
    part: Int,
    sequence: Int
  ): FindawayReadingOrderItem? {
    val byPart = this.readingOrderByPartAndSequence[part]
    if (byPart != null) {
      return byPart[sequence]
    }
    return null
  }

  override val wholeBookDownloadTask: PlayerDownloadWholeBookTaskType
    get() = this.wholeDownloadTask
}
