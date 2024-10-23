package org.librarysimplified.audiobook.mocking

import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerBookID
import org.librarysimplified.audiobook.api.PlayerDownloadTaskType
import org.librarysimplified.audiobook.api.PlayerDownloadWholeBookTaskType
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOC
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A fake audio book.
 */

class MockingAudioBook(
  override val palaceId: PlayerPalaceID,
  override val id: PlayerBookID,
  val players: (MockingAudioBook) -> MockingPlayer
) : PlayerAudioBookType {

  val statusEvents: BehaviorSubject<PlayerReadingOrderItemDownloadStatus> = BehaviorSubject.create()
  val spineItems: MutableList<MockingReadingOrderItem> = mutableListOf()

  private val isClosedNow =
    AtomicBoolean(false)
  private val wholeTask =
    MockingDownloadWholeBookTask(this, URI.create("urn:unsupported"))

  override var supportsStreaming: Boolean = false

  override val supportsIndividualChapterDeletion: Boolean
    get() = true

  override val supportsIndividualChapterDownload: Boolean
    get() = true

  override val readingOrder: List<PlayerReadingOrderItemType>
    get() = this.spineItems

  override val readingOrderByID: Map<PlayerManifestReadingOrderID, PlayerReadingOrderItemType>
    get() = this.spineItems.associateBy(keySelector = { e -> e.id }, valueTransform = { e -> e })

  override val readingOrderElementDownloadStatus: Observable<PlayerReadingOrderItemDownloadStatus>
    get() = this.statusEvents

  override val downloadTasks: List<PlayerDownloadTaskType>
    get() = listOf(this.wholeTask)

  override val downloadTasksByID: Map<PlayerManifestReadingOrderID, PlayerDownloadTaskType>
    get() = TODO()

  override val wholeBookDownloadTask: PlayerDownloadWholeBookTaskType
    get() = this.wholeTask

  override fun replaceManifest(
    manifest: PlayerManifest
  ): CompletableFuture<Unit> {
    val future = CompletableFuture<Unit>()
    future.complete(Unit)
    return future
  }

  override val tableOfContents: PlayerManifestTOC
    get() = TODO("Not yet implemented")

  override val manifest: PlayerManifest
    get() = TODO("Not yet implemented")

  override fun createPlayer(): MockingPlayer {
    check(!this.isClosed) { "Audio book has been closed" }

    return this.players.invoke(this)
  }

  override fun close() {
    if (this.isClosedNow.compareAndSet(false, true)) {
      // No resources to clean up
    }
  }

  override val isClosed: Boolean
    get() = this.isClosedNow.get()
}
