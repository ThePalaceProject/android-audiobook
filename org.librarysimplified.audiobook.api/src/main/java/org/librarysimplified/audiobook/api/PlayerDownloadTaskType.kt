package org.librarysimplified.audiobook.api

/**
 * A download in progress. If the part of the book to which this download task refers is already
 * downloaded, the task completes instantly.
 */

interface PlayerDownloadTaskType {

  /**
   * Run the download task.
   */

  fun fetch()

  /**
   * Cancel the download in progress if the download is currently running, and delete any partially
   * downloaded data. Has no effect if the download task is not currently downloading anything,
   * although implementations are permitted to broadcast events indicating the current state of the
   * download.
   */

  fun cancel()

  /**
   * Delete the downloaded data (if any). The method has no effect if the data has not been
   * downloaded. If the download is in progress, the download is cancelled as if the `cancel`
   * method had been called.
   */

  fun delete()

  /**
   * Checks if a specific spine element belongs to this task's spine items list.
   */

  fun fulfillsSpineElement(spineElement: PlayerSpineElementType): Boolean

  /**
   * The current download progress in the range [0, 1]
   */

  val progress: Double

  /**
   * The list of spine items related to the download task.
   */

  val spineItems: List<PlayerSpineElementType>
}
