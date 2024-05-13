package org.librarysimplified.audiobook.manifest.api

import com.io7m.kabstand.core.IntervalL
import com.io7m.kabstand.core.IntervalTreeType
import org.joda.time.Duration

/**
 * An immutable table-of-contents object.
 */

data class PlayerManifestTOC(
  val tocItemsInOrder: List<PlayerManifestTOCItem>,
  val tocItemsByInterval: Map<IntervalL, PlayerManifestTOCItem>,
  val tocItemTree: IntervalTreeType<Long>,
  val readingOrderIntervals: Map<PlayerManifestReadingOrderID, IntervalL>
) {
  private val highestAbsoluteOffset =
    this.tocItemTree.maximum()?.upper() ?: 0L

  /**
   * The total duration of the entire book.
   */

  val totalDuration =
    Duration.millis(this.highestAbsoluteOffset)

  fun lookupTOCItem(
    id: PlayerManifestReadingOrderID,
    offset: Long
  ): PlayerManifestTOCItem? {
    val readingOrderInterval =
      this.readingOrderIntervals[id] ?: return null
    val absoluteOffset =
      readingOrderInterval.lower + offset

    if (absoluteOffset < 0) {
      return this.tocItemsInOrder.firstOrNull()
    }

    if (absoluteOffset >= this.highestAbsoluteOffset) {
      return this.tocItemsInOrder.lastOrNull()
    }

    val tocsIntersecting = this.tocItemTree.overlapping(IntervalL(absoluteOffset, absoluteOffset))
    if (tocsIntersecting.isEmpty()) {
      return null
    }

    // We assume that there are no overlapping TOC items; it should be impossible for TOC items
    // to overlap given the way manifests are constructed.
    return this.tocItemsByInterval[tocsIntersecting.first()]
  }

  fun totalDurationRemaining(
    tocItem: PlayerManifestTOCItem,
    readingOrderItemOffsetMilliseconds: Long
  ): Duration {
    /*
     * First, determine the current position on the absolute timeline. Essentially, we look
     * up the reading order item that owns this TOC item, and add the given reading order item
     * offset in milliseconds to the absolute time of the start of the reading order item.
     */

    val readingOrderItem =
      tocItem.readingOrderLink
    val readingOrderInterval =
      this.readingOrderIntervals[readingOrderItem.id] ?: return Duration.ZERO
    val absoluteReadingOrderPosition =
      readingOrderInterval.lower + readingOrderItemOffsetMilliseconds

    /*
     * We then subtract the absolute position from the total duration of the book.
     */

    return this.totalDuration.minus(Duration.millis(absoluteReadingOrderPosition))
  }
}
