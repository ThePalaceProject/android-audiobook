package org.librarysimplified.audiobook.api

/**
 * The download status of a particular part of a book.
 */

sealed class PlayerReadingOrderItemDownloadStatus {

  /**
   * The spine element to which this download status refers.
   */

  abstract val readingOrderItem: PlayerReadingOrderItemType

  /**
   * The part of the book has not been downloaded. If the underlying audio engine supports
   * streaming, then attempting to play this part of the book will stream it from a remote
   * server.
   */

  data class PlayerReadingOrderItemNotDownloaded(
    override val readingOrderItem: PlayerReadingOrderItemType
  ) : PlayerReadingOrderItemDownloadStatus()

  /**
   * The part of the book is currently downloading.
   */

  data class PlayerReadingOrderItemDownloading(
    override val readingOrderItem: PlayerReadingOrderItemType,
    val progress: PlayerDownloadProgress
  ) : PlayerReadingOrderItemDownloadStatus()

  /**
   * The part of the book is completely downloaded.
   */

  data class PlayerReadingOrderItemDownloaded(
    override val readingOrderItem: PlayerReadingOrderItemType
  ) : PlayerReadingOrderItemDownloadStatus()

  /**
   * Downloading this part of the book failed.
   */

  data class PlayerReadingOrderItemDownloadFailed(
    override val readingOrderItem: PlayerReadingOrderItemType,
    val exception: Exception?,
    val message: String
  ) : PlayerReadingOrderItemDownloadStatus()

  /**
   * Downloading this part of the book failed due to an (apparently) expired link. The download
   * will likely succeed if the manifest is reloaded containing a fresh set of links.
   */

  data class PlayerReadingOrderItemDownloadExpired(
    override val readingOrderItem: PlayerReadingOrderItemType,
    val exception: Exception?,
    val message: String
  ) : PlayerReadingOrderItemDownloadStatus()
}
