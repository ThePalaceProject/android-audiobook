package org.librarysimplified.audiobook.api

import io.reactivex.Observable
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.manifest.api.PlayerManifestTOC
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import java.io.Closeable
import java.util.concurrent.CompletableFuture

/**
 * An instance of an audio book. The audio book must be closed when it is no longer needed.
 */

interface PlayerAudioBookType : Closeable {

  /**
   * The raw OPDS ID of the book.
   */

  val palaceId: PlayerPalaceID
    get() = this.manifest.palaceId

  /**
   * `true` if [close] has been called
   */

  val isClosed: Boolean

  /**
   * A unique identifier for the book.
   */

  val id: PlayerBookID

  /**
   * True iff the underlying audio book supports streaming. That is, it's not necessary to download
   * a book part before it's possible to play that part.
   */

  val supportsStreaming: Boolean

  /**
   * True iff the underlying audio engine supports the deletion of individual chapters via
   * the PlayerDownloadTaskType interface. If this is false, local book data may only be
   * deleted via the `deleteLocalChapterData` method.
   */

  val supportsIndividualChapterDeletion: Boolean

  /**
   * True iff the underlying audio engine supports the download of individual chapters via
   * the PlayerDownloadTaskType interface. If this is false, books may only be downloaded using
   * the [wholeBookDownloadTask].
   */

  val supportsIndividualChapterDownload: Boolean

  /**
   * The list of reading order items in reading order.
   */

  val readingOrder: List<PlayerReadingOrderItemType>

  /**
   * The list of download tasks for each available audiobook file.
   */

  val downloadTasks: List<PlayerDownloadTaskType>

  /**
   * The list of download tasks for each available audiobook file.
   */

  val downloadTasksByID: Map<PlayerManifestReadingOrderID, PlayerDownloadTaskType>

  /**
   * The reading order items organized by their unique IDs.
   */

  val readingOrderByID: Map<PlayerManifestReadingOrderID, PlayerReadingOrderItemType>

  /**
   * An observable publishing changes to the current download status of the part.
   */

  val readingOrderElementDownloadStatus: Observable<PlayerReadingOrderItemDownloadStatus>

  /**
   * Create a player for the audio book. The player must be closed when it is no longer needed.
   */

  fun createPlayer(): PlayerType

  /**
   * A download task that downloads all chapters and can also be used to delete local book data.
   */

  val wholeBookDownloadTask: PlayerDownloadWholeBookTaskType

  /**
   * Replace the current manifest with a new manifest.
   *
   * Implementations are permitted to ignore new manifests if the underlying audio engine does not
   * support replacing manifests.
   *
   * Implementations are permitted to ignore any information in the new manifest that cannot be
   * reasonably expected to replace existing information.
   *
   * Implementations are permitted to ignore manifests that are in some way invalid, such as not
   * having the same number of spine elements as the existing loaded manifest.
   *
   * Implementations are required to reject manifests that do not have the same identifier as the
   * old manifest.
   *
   * @see [id]
   */

  fun replaceManifest(manifest: PlayerManifest): CompletableFuture<Unit>

  /**
   * @return The table of contents generated from the manifest
   */

  val tableOfContents: PlayerManifestTOC

  /**
   * The most recent manifest
   */

  val manifest: PlayerManifest
}
