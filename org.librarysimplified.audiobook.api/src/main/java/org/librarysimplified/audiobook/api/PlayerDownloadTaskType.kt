package org.librarysimplified.audiobook.api

import java.net.URI

/**
 * A download in progress. If the part of the book to which this download task refers is already
 * downloaded, the task completes instantly.
 */

interface PlayerDownloadTaskType {

  /**
   * The URI that must be passed to the player to play the content associated with this download
   * item. This might refer to a remote resource, or it might be a file:// URI, or even something
   * more abstract. The underlying player engine is guaranteed to be configured such that it will
   * understand any kind of URI that can be returned by this method.
   */

  val playbackURI: URI

  /**
   * The index of the download task (or `0` for "whole book" tasks).
   */

  val index: Int

  /**
   * The current status of the task
   */

  val status: PlayerDownloadTaskStatus

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
   * The current download progress in the range [0, 1]
   */

  val progress: PlayerDownloadProgress

  /**
   * The list of reading order items related to the download task.
   */

  val readingOrderItems: List<PlayerReadingOrderItemType>
}
