package org.librarysimplified.audiobook.api

import org.joda.time.Duration
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID

/**
 * A reading order item.
 */

interface PlayerReadingOrderItemType {

  /**
   * The book to which this reading order element belongs.
   */

  val book: PlayerAudioBookType

  /**
   * The index of the reading order item within the reading order.
   *
   * The first item in the reading order, if it exists, is guaranteed to have index = 0.
   * The next reading order item, if it exists, is guaranteed to be at index + 1.
   */

  val index: Int

  /**
   * The next reading order item, if one exists. This is null if and only if the current reading order element
   * is the last one in the book.
   */

  val next: PlayerReadingOrderItemType?

  /**
   * The previous reading order item, if one exists. This is null if and only if the current reading order element
   * is the first one in the book.
   */

  val previous: PlayerReadingOrderItemType?

  /**
   * The length of the reading order item, if available.
   */

  val duration: Duration

  /**
   * The unique identifier for the reading order item.
   */

  val id: PlayerManifestReadingOrderID

  /**
   * The latest published download status for the reading order item.
   */

  val downloadStatus: PlayerReadingOrderItemDownloadStatus

  /**
   * The previously published download status for the reading order item.
   */

  val downloadStatusPrevious: PlayerReadingOrderItemDownloadStatus

  /**
   * `true` if downloading individual chapters is supported by the underlying engine.
   */

  val downloadTasksSupported: Boolean

  /**
   * The start of this reading order item expressed as a player position
   */

  val startingPosition: PlayerPosition
}
