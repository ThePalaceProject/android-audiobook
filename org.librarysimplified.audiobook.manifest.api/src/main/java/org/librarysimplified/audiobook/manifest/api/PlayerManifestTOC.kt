package org.librarysimplified.audiobook.manifest.api

import com.io7m.kabstand.core.IntervalTreeType
import org.joda.time.Duration

/**
 * An immutable table-of-contents object.
 */

data class PlayerManifestTOC(
  val tocItemsInOrder: List<PlayerManifestTOCItem>,
  val tocItemsByInterval: Map<PlayerMillisecondsAbsoluteInterval, PlayerManifestTOCItem>,
  val tocItemTree: IntervalTreeType<PlayerMillisecondsAbsolute>,
  val readingOrderItemTree: IntervalTreeType<PlayerMillisecondsAbsolute>,
  val readingOrderItemsByInterval: Map<PlayerMillisecondsAbsoluteInterval, PlayerManifestReadingOrderID>,
  val readingOrderIntervals: Map<PlayerManifestReadingOrderID, PlayerMillisecondsAbsoluteInterval>
) {
  init {
    check(this.tocItemsInOrder.isNotEmpty()) {
      "Table of contents is not permitted to be empty."
    }
  }

  private val highestAbsoluteOffset: PlayerMillisecondsAbsolute =
    this.tocItemTree.maximum()?.upper() ?: PlayerMillisecondsAbsolute(0L)

  /**
   * The total duration of the entire book.
   */

  val totalDuration =
    Duration.millis(this.highestAbsoluteOffset.value)

  /**
   * Find the TOC item that intersects the given reading order item at the given millisecond
   * offset.
   */

  fun lookupTOCItem(
    id: PlayerManifestReadingOrderID,
    offset: PlayerMillisecondsReadingOrderItem
  ): PlayerManifestTOCItem {
    val readingOrderInterval =
      this.readingOrderIntervals[id]
        ?: throw IllegalArgumentException("No such reading order item: $id")

    val absoluteOffset: PlayerMillisecondsAbsolute =
      readingOrderInterval.lower + offset

    if (absoluteOffset >= this.highestAbsoluteOffset) {
      return this.tocItemsInOrder.last()
    }

    val tocsIntersecting =
      this.tocItemTree.overlapping(
        PlayerMillisecondsAbsoluteInterval(absoluteOffset, absoluteOffset)
      )

    if (tocsIntersecting.isEmpty()) {
      return this.tocItemsInOrder.last()
    }

    // We assume that there are no overlapping TOC items; it should be impossible for TOC items
    // to overlap given the way manifests are constructed.
    return this.tocItemsByInterval[tocsIntersecting.first()]!!
  }

  fun positionMetadataFor(
    readingOrderItemID: PlayerManifestReadingOrderID,
    readingOrderItemOffset: PlayerMillisecondsReadingOrderItem,
    readingOrderItemInterval: PlayerMillisecondsAbsoluteInterval
  ): PlayerManifestPositionMetadata {
    val tocItem = this.lookupTOCItem(readingOrderItemID, readingOrderItemOffset)

    /*
     * In order to determine how far along the current TOC item we are, we need to:
     *
     * 1. Get the absolute interval of the TOC item.
     * 2. Get the absolute interval of the current reading order item.
     * 3. Add the current reading-order-item-relative time in milliseconds to the lower
     *    bound of the reading order item interval. This gives us the absolute time in
     *    milliseconds of our position within the entire book.
     * 4. Subtract the lower bound of the absolute interval of the TOC item from our
     *    absolute position value. This gives us our position in milliseconds relative to
     *    the current TOC item.
     * 5. Our position within the TOC item as a real value is then just the
     *    TOC-relative position divided by the TOC length.
     */

    val tocItemInterval: PlayerMillisecondsAbsoluteInterval =
      tocItem.intervalAbsoluteMilliseconds
    val offsetAbsolute: PlayerMillisecondsAbsolute =
      readingOrderItemInterval.lower + readingOrderItemOffset

    val tocItemOffset: PlayerMillisecondsTOC =
      PlayerMillisecondsTOC((offsetAbsolute - tocItemInterval.lower).value)
    val tocItemDuration =
      tocItem.durationMilliseconds
    val tocItemPosition =
      Duration.millis(tocItemOffset.value)
    val tocItemRemaining =
      Duration.millis(tocItemDuration).minus(tocItemPosition)
    val tocItemProgress =
      tocItemOffset.value.toDouble() / tocItemDuration.toDouble()

    /*
     * The current progress throughout the entire book is simply the absolute offset we
     * calculated earlier, divided by the length of the book in milliseconds. Accordingly,
     * the total remaining time is simply the length of the book minus the current absolute
     * offset.
     */

    val bookProgress =
      offsetAbsolute.value.toDouble() / this.totalDuration.millis.toDouble()
    val durationRemaining =
      this.totalDuration.minus(Duration.millis(offsetAbsolute.value))

    return PlayerManifestPositionMetadata(
      tocItem = tocItem,
      tocItemPosition = tocItemPosition,
      tocItemRemaining = tocItemRemaining,
      totalRemainingBookTime = durationRemaining,
      chapterProgressEstimate = tocItemProgress,
      bookProgressEstimate = bookProgress
    )
  }

  /**
   * Given a reading order item and a millisecond offset relative to that reading order item,
   * return the amount of time left in the book.
   */

  fun totalDurationRemaining(
    readingOrderItemID: PlayerManifestReadingOrderID,
    readingOrderItemOffsetMilliseconds: PlayerMillisecondsReadingOrderItem
  ): Duration {
    val readingOrderInterval =
      this.readingOrderIntervals[readingOrderItemID]
        ?: throw IllegalArgumentException("No such reading order item: $readingOrderItemID")

    val offsetAbsolute: PlayerMillisecondsAbsolute =
      readingOrderInterval.lower + readingOrderItemOffsetMilliseconds

    return this.totalDuration.minus(Duration.millis(offsetAbsolute.value))
  }
}
